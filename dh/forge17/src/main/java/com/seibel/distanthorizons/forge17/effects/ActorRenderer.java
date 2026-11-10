package com.seibel.distanthorizons.forge17.effects;

import com.seibel.distanthorizons.api.methods.events.abstractEvents.*;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.*;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import com.seibel.distanthorizons.api.enums.config.EDhApiDepthDirection;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.*;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.AbstractDhRenderApiDefinition;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.render.RenderBufferHandler;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IIrisAccessor;
import com.seibel.distanthorizons.forge17.actors.*;
import com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftRenderWrapper;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraftforge.client.event.RenderPlayerEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.FloatBuffer;
import java.util.*;

/** Shared render owner for authoritative actor visuals. No world insertion or entity ticking. */
public final class ActorRenderer {
    public static ActorRenderer INSTANCE;
    private final DhApiRenderParam frame=new DhApiRenderParam();
    private DhApiRenderParam source;
    private final ModelCapture capture=new ModelCapture();
    private final ModelScene scene=new ModelScene();
    private final ShaderActorClouds clouds=new ShaderActorClouds();
    private final ActorTerrainVisibility terrain=new ActorTerrainVisibility();
    private final Map<UUID,Contact> contacts=new LinkedHashMap<>();
    private final Set<UUID> nativeDrawn=new HashSet<>(),nativeVehicles=new HashSet<>();
    private final McheliActors vehicles;
    private final List<ActorModel> models=new ArrayList<>(PlayerFrame.MAX_PLAYERS+VehicleFrame.MAX_VEHICLES);
    private final List<ActorModel> visibleModels=new ArrayList<>(PlayerFrame.MAX_PLAYERS+VehicleFrame.MAX_VEHICLES);
    private final DhApiMat4f nativeMvp=new DhApiMat4f(),dhMvp=new DhApiMat4f(),inverseScene=new DhApiMat4f();
    private final DhApiMat4f localProjection=new DhApiMat4f(),localMvp=new DhApiMat4f(),inverseLocal=new DhApiMat4f();
    private final int[] bounds=new int[4];
    private final FloatBuffer matrix=BufferUtils.createFloatBuffer(16);
    private Object world,connection;
    private long generation,sequence=-1,receipt;
    private double nx,ny,nz,dx,dy,dz;
    private int nativeFramebuffer,shaderFramebuffer,preparedWidth,preparedHeight;
    private boolean valid,drawing,failed,sceneComplete;
    private int drawn,unknown;
    private String status="waiting for terrain view";
    public String diagnostic() { return "DH actor models: "+status+" drawn="+drawn+" native="+nativeDrawn.size()+" unknown="+unknown+" cached="+contacts.size(); }
    public ActorRenderer() {
        INSTANCE=this;
        vehicles=new McheliActors(()->vehiclesOwnLegacy(),this::replaceVehicle,this::observedVehicle);
        ApiEventInjector.INSTANCE.bind(DhApiBeforeRenderSetupEvent.class,new DhApiBeforeRenderSetupEvent() {
            public void beforeSetup(DhApiEventParam<DhApiRenderParam> event) {
                if(!shadow()) nativeFramebuffer=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            }
        });
        ApiEventInjector.INSTANCE.bind(DhApiBeforeRenderCleanupEvent.class,new DhApiBeforeRenderCleanupEvent() {
            public void beforeCleanup(DhApiEventParam<DhApiRenderParam> event) { snapshot(event.value); }
        });
    }
    private static boolean shadow() {
        IIrisAccessor iris=ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
        return (iris!=null && iris.isRenderingShadowPass()) || FmurPlayerBridge.auxiliaryView();
    }
    private void snapshot(DhApiRenderParam input) {
        if(!Config.Client.Advanced.Graphics.GenericRendering.enablePlayerLodSupport.get() && !owned() && !vehicles.available()) return;
        if(shadow() || !(input.clientLevelWrapper instanceof IClientLevelWrapper)) return;
        IClientLevelWrapper wrapper=(IClientLevelWrapper)input.clientLevelWrapper;
        if(wrapper.getWrappedMcObject()!=Minecraft.getMinecraft().theWorld || wrapper.getDhLevel()==null) return;
        RenderBufferHandler buffers=wrapper.getDhLevel().getRenderBufferHandler();if(buffers==null) return;
        source=input;frame.update(input);generation=buffers.getCoverageGeneration();
        nativeMvp.set(frame.mcProjectionMatrix);nativeMvp.multiply(frame.mcModelViewMatrix);
        nx=RenderManager.instance.viewerPosX;ny=RenderManager.instance.viewerPosY;nz=RenderManager.instance.viewerPosZ;
        com.seibel.distanthorizons.core.util.math.DhVec3d origin=MinecraftRenderWrapper.INSTANCE.getCameraExactPosition();
        dx=origin.x;dy=origin.y;dz=origin.z;valid=true;
    }
    @SubscribeEvent public void start(TickEvent.RenderTickEvent event) {
        if(event.phase!=TickEvent.Phase.START) return;
        valid=false;sceneComplete=false;nativeDrawn.clear();nativeVehicles.clear();drawn=unknown=0;status="waiting for terrain view";
        Minecraft mc=Minecraft.getMinecraft();
        if(world!=mc.theWorld || connection!=mc.getNetHandler()) {
            clear();world=mc.theWorld;connection=mc.getNetHandler();sequence=-1;receipt=0;failed=false;
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void nativePre(RenderPlayerEvent.Pre event) {
        if(failed || drawing || !valid || !owned() || shadow() || event.entityPlayer==Minecraft.getMinecraft().thePlayer) return;
        if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=nativeFramebuffer) return;
        // Only actors with an authoritative recipient record are eligible for handoff.
        for(PlayerFrame.Pose pose:PlayerClient.INSTANCE.frames().players()) if(pose.uuid.equals(event.entityPlayer.getUniqueID())) {
            if(!DistantSmokeRenderer.nativeModelVisible(event.entityPlayer,8)) event.setCanceled(true);
            break;
        }
    }
    @SubscribeEvent public void nativePost(RenderPlayerEvent.Post event) {
        if(!drawing && valid && !shadow() && GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)==nativeFramebuffer)
            nativeDrawn.add(event.entityPlayer.getUniqueID());
    }
    static int actorDepth() { return INSTANCE!=null && INSTANCE.sceneComplete?INSTANCE.scene.depthTexture():0; }
    static int actorColour() { return INSTANCE!=null && INSTANCE.sceneComplete?INSTANCE.scene.colourTexture():0; }
    private boolean vehiclesOwnLegacy() { return vehicles!=null && vehicles.ownsLegacy(); }
    private boolean primaryNativeView() {
        return !failed && !drawing && valid && !shadow() && world==Minecraft.getMinecraft().theWorld
            && GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)==nativeFramebuffer;
    }
    private boolean replaceVehicle(net.minecraft.entity.Entity entity) {
        return primaryNativeView() && vehicles.contains(entity) && !DistantSmokeRenderer.nativeModelVisible(entity,vehicles.radius(entity));
    }
    private boolean observedVehicle(net.minecraft.entity.Entity entity) {
        if(!primaryNativeView() || !DistantSmokeRenderer.nativeModelVisible(entity,vehicles.radius(entity))) return false;
        nativeVehicles.add(entity.getUniqueID());return true;
    }
    private boolean owned() { return PlayerClient.INSTANCE!=null && PlayerClient.INSTANCE.ownsLegacy(); }
    public static void render(boolean shaders) {
        if(INSTANCE!=null) INSTANCE.draw(shaders);
    }
    private void draw(boolean shaders) {
        Minecraft mc=Minecraft.getMinecraft();PlayerClient client=PlayerClient.INSTANCE;
        if(failed || !valid || world!=mc.theWorld || client==null || shadow()) return;
        boolean playersWanted=client.fmurReady() && (Config.Client.Advanced.Graphics.GenericRendering.enablePlayerLodSupport.get() || owned());
        boolean vehiclesWanted=vehicles.available();
        if(!playersWanted && !vehiclesWanted) return;
        ShaderEffectsTarget target=shaders?ShaderEffectsTarget.current():null;
        if(shaders && target==null) { status="unsupported shader target";return; }
        if(!clouds.refresh(target)) { status="waiting for shader cloud contract";return; }
        if(!shaders && GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=mc.getFramebuffer().framebufferObject) return;
        int terrainDepth=target==null?com.seibel.distanthorizons.core.render.DhApiRenderProxy.activeOpenGlDhDepthTextureId:target.terrainDepth;
        if(terrainDepth<=0 || !terrain.begin(source,mc.theWorld,generation)) return;
        int width=target==null?mc.getFramebuffer().framebufferWidth:target.sceneWidth;
        int height=target==null?mc.getFramebuffer().framebufferHeight:target.sceneHeight;
        if(!ModelCapture.supportsSize(width,height)) { status="unsupported target "+width+"x"+height;terrain.clear();return; }
        status="preparing models";
        try {
            if(preparedWidth!=width || preparedHeight!=height) {
                capture.prepare(width,height);scene.prepare(width,height);preparedWidth=width;preparedHeight=height;
            }
            dhMvp.set(target==null?frame.dhProjectionMatrix:target.terrainProjection);dhMvp.multiply(frame.dhModelViewMatrix);
            DhApiMat4f translation=new DhApiMat4f();translation.m00=translation.m11=translation.m22=translation.m33=1;translation.m03=(float)(nx-dx);translation.m13=(float)(ny-dy);translation.m23=(float)(nz-dz);
            dhMvp.multiply(translation);
            if(!ModelProjection.inverse(nativeMvp,inverseScene)) { status="invalid native projection";return; }
            if(playersWanted) client.setRenderingReady();if(vehiclesWanted) vehicles.ready();
            if(!owned() && !vehiclesOwnLegacy()) { status="awaiting server ownership";return; }
            status="active experimental primary view";
            if(owned()) update(client.frames());
            boolean reverse=SingletonInjector.INSTANCE.get(AbstractDhRenderApiDefinition.class).getDepthDirection()==EDhApiDepthDirection.REVERSE_Z;
            models.clear();visibleModels.clear();long now=System.nanoTime();
            if(owned()) for(Map.Entry<UUID,Contact> entry:contacts.entrySet())
                if(!nativeDrawn.contains(entry.getKey()) && entry.getValue().track.sample(now,entry.getValue().sample)) models.add(entry.getValue());
            vehicles.collect(models,nativeVehicles);
            // Spend the bounded provenance work on nearer models first, before expensive preparation.
            models.sort((a,b)->Double.compare(distance(a),distance(b)));
            for(ActorModel model:models) {
                double radius=model.radius();
                if(!Double.isFinite(radius) || radius<=0 || radius>128) continue;
                if(!ModelBounds.project(nativeMvp,model.x()-nx,model.centerY()-ny,model.z()-nz,radius,width,height,bounds)) continue;
                if(!terrain.isKnown(nx,ny,nz,model.x(),model.centerY(),model.z(),radius)) { unknown++;continue; }
                if(model.prepare()) visibleModels.add(model);
            }
            if(visibleModels.isEmpty()) return;
            // Opaque pixels retain shared depth; alpha-bearing parts follow back-to-front actor ordering.
            visibleModels.sort((a,b)->Double.compare(distance(b),distance(a)));
            boolean completed=false;scene.begin(width,height,reverse);
            try {
                for(ActorModel model:visibleModels) {
                    double radius=model.radius();
                    if(!ModelBounds.project(nativeMvp,model.x()-nx,model.centerY()-ny,model.z()-nz,radius,width,height,bounds)) continue;
                    double x=model.x()-nx,y=model.y()-ny,z=model.z()-nz;
                    double eyeDepth=-(frame.mcModelViewMatrix.m20*x+frame.mcModelViewMatrix.m21*(model.centerY()-ny)+frame.mcModelViewMatrix.m22*z+frame.mcModelViewMatrix.m23);
                    if(!ModelProjection.prepare(frame.mcProjectionMatrix,eyeDepth,radius,localProjection)) continue;
                    localMvp.set(localProjection);localMvp.multiply(frame.mcModelViewMatrix);
                    if(!ModelProjection.inverse(localMvp,inverseLocal)) continue;
                    boolean modelComplete=false;
                    if(!capture.begin(width,height,bounds[0],bounds[1],bounds[2],bounds[3],localProjection)) continue;
                    try { drawModel(model,x,y,z);modelComplete=true; }
                    finally { capture.end(modelComplete); }
                    drawn++;capture.composite(inverseLocal,dhMvp,nativeMvp,0,0,0,terrainDepth,target==null?0:target.nativeSceneDepth,reverse,true);
                }
                completed=true;
            } finally { scene.end(completed); }
            if(drawn==0) return;
            int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),oldDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            try {
                if(target!=null) bindTarget(target);
                scene.composite(inverseScene,dhMvp,nativeMvp,frame.mcModelViewMatrix,terrainDepth,target==null?0:target.nativeSceneDepth,shaders,clouds.texture,clouds.distance);
                sceneComplete=true;
            } finally { GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw); }
        } catch(Exception | LinkageError error) {
            failed=true;status="render failure (withheld)";org.apache.logging.log4j.LogManager.getLogger("DistantHorizons").error("DH player presentation stopped after render failure",error);
        } finally { terrain.clear(); }
    }
    private double distance(ActorModel p) { double x=p.x()-nx,y=p.centerY()-ny,z=p.z()-nz;return x*x+y*y+z*z; }
    private void update(PlayerFrameCache cache) {
        if(sequence==cache.sequence() && receipt==cache.received()) {
            if(cache.players().isEmpty()) clearPlayers();return;
        }
        sequence=cache.sequence();receipt=cache.received();Set<UUID> retained=new HashSet<>();
        for(PlayerFrame.Pose pose:cache.players()) {
            retained.add(pose.uuid);Contact contact=contacts.get(pose.uuid);
            if(contact==null) { contact=new Contact(pose.uuid);contacts.put(pose.uuid,contact); }
            contact.track.observe(pose,sequence,receipt,cache.interval());
        }
        Iterator<Map.Entry<UUID,Contact>> iterator=contacts.entrySet().iterator();
        while(iterator.hasNext()) { Map.Entry<UUID,Contact> entry=iterator.next();if(!retained.contains(entry.getKey())) { entry.getValue().close();iterator.remove(); } }
    }
    private void drawModel(ActorModel model,double x,double y,double z) throws Exception {
        float partial=model.partial();
        int mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);float lightX=OpenGlHelper.lastBrightnessX,lightY=OpenGlHelper.lastBrightnessY;
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();drawing=true;
        try {
            load(frame.mcModelViewMatrix);GL20.glUseProgram(0);
            Minecraft.getMinecraft().entityRenderer.enableLightmap(partial);
            RenderHelper.enableStandardItemLighting();GL11.glEnable(GL11.GL_NORMALIZE);GL11.glDisable(GL11.GL_FOG);
            int units=GL11.glGetInteger(GL13.GL_MAX_TEXTURE_UNITS);
            for(int unit=0;unit<units;unit++) {
                int id=GL13.GL_TEXTURE0+unit;OpenGlHelper.setActiveTexture(id);
                GL11.glDisable(GL11.GL_TEXTURE_1D);GL11.glDisable(GL12.GL_TEXTURE_3D);GL11.glDisable(GL13.GL_TEXTURE_CUBE_MAP);
                GL11.glDisable(GL11.GL_TEXTURE_GEN_S);GL11.glDisable(GL11.GL_TEXTURE_GEN_T);GL11.glDisable(GL11.GL_TEXTURE_GEN_R);GL11.glDisable(GL11.GL_TEXTURE_GEN_Q);
                if(id==OpenGlHelper.defaultTexUnit || id==OpenGlHelper.lightmapTexUnit) GL11.glEnable(GL11.GL_TEXTURE_2D);else GL11.glDisable(GL11.GL_TEXTURE_2D);
                if(id==OpenGlHelper.defaultTexUnit) GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL11.GL_TEXTURE_ENV_MODE,GL11.GL_MODULATE);
                if(id==OpenGlHelper.lightmapTexUnit) {
                    GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL11.GL_TEXTURE_ENV_MODE,GL13.GL_COMBINE);
                    GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL13.GL_COMBINE_RGB,GL11.GL_MODULATE);
                    GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL13.GL_SOURCE0_RGB,GL13.GL_PREVIOUS);
                    GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL13.GL_SOURCE1_RGB,GL11.GL_TEXTURE);
                    GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL13.GL_COMBINE_ALPHA,GL11.GL_REPLACE);
                    GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL13.GL_SOURCE0_ALPHA,GL13.GL_PREVIOUS);
                }
            }
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            int light=model.light();OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light&65535,light>>>16);
            GL11.glEnable(GL11.GL_BLEND);GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1,1,1,1);
            model.draw(x,y,z);
        } finally {
            Minecraft.getMinecraft().entityRenderer.disableLightmap(partial);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,lightX,lightY);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopMatrix();GL11.glMatrixMode(mode);drawing=false;
        }
    }
    private void load(DhApiMat4f value) {
        float[] rows=new float[16];value.putValuesInArray(rows);matrix.clear();
        for(int column=0;column<4;column++) for(int row=0;row<4;row++) matrix.put(rows[row*4+column]);matrix.flip();GL11.glLoadMatrix(matrix);
    }
    private void bindTarget(ShaderEffectsTarget target) {
        if(shaderFramebuffer==0) shaderFramebuffer=GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,shaderFramebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,target.sceneColour,0);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT1,GL11.GL_TEXTURE_2D,target.particleMaterial,0);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,target.sceneDepth,0);
        java.nio.IntBuffer outputs=BufferUtils.createIntBuffer(2);outputs.put(GL30.GL_COLOR_ATTACHMENT0);
        if(target.particleMaterial>0) outputs.put(GL30.GL_COLOR_ATTACHMENT1);outputs.flip();GL20.glDrawBuffers(outputs);
        if(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Incomplete actor output target");
    }
    private void clearPlayers() { for(Contact contact:contacts.values()) contact.close();contacts.clear(); }
    private void clear() { clearPlayers();vehicles.clear();terrain.clear(); }
    private final class Contact implements ActorModel {
        final UUID uuid;
        float partial;
        Contact(UUID uuid) { this.uuid=uuid; }
        final PlayerTrack track=new PlayerTrack();final PlayerTrack.Sample sample=new PlayerTrack.Sample();
        VisualPlayerActor actor;long revision;
        public double x() { return sample.x; }
        public double y() { return sample.y; }
        public double centerY() { return sample.y+1; }
        public double z() { return sample.z; }
        public double radius() { return 8; }
        public float partial() { return partial; }
        public int light() { return actor.getBrightnessForRender(partial); }
        public boolean prepare() throws Exception {
            PlayerAppearance appearance=PlayerClient.INSTANCE.frames().appearance(uuid,sample.entityId,sample.revision);
            if(appearance==null) return false;
            boolean fmur=PlayerClient.INSTANCE.frames().fmurEnabled(sample.flags,Config.Client.Advanced.Graphics.GenericRendering.extendFlansTrails.get());
            if(actor==null || actor.getEntityId()!=sample.entityId || revision!=sample.revision) {
                PlayerAppearance.Decoded decoded=appearance.appearance();if(actor!=null) actor.close();
                actor=new VisualPlayerActor(Minecraft.getMinecraft().theWorld,decoded.profile,sample.entityId);
                actor.appearance(decoded,fmur);revision=sample.revision;
            }
            actor.support(fmur);partial=actor.pose(sample);return true;
        }
        public void draw(double x,double y,double z) throws Exception {
            try(AutoCloseable scope=actor.beginExtensions()) {
                Render renderer=RenderManager.instance.getEntityRenderObject(actor);
                if(renderer!=null) renderer.doRender(actor,x,y,z,actor.rotationYaw,partial);
            }
        }
        void close() { if(actor!=null) actor.close(); }
    }
}
