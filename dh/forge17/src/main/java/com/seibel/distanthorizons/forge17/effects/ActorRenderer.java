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
    final ActorCameraPass cameraPass=new ActorCameraPass(this);
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
    private int drawn,unknown,outside,unprepared,projectionRejected,captureRejected;
    private String status="waiting for terrain view";
    private String lastRejection="none";
    private long rejectionTime;
    public String diagnostic() { return "DH actor models: "+status+" drawn="+drawn+" native="+(nativeDrawn.size()+nativeVehicles.size())+" unknown="+unknown
        +" outside="+outside+" unprepared="+unprepared+" projection="+projectionRejected+" capture="+captureRejected+" candidates="+models.size()+" cached="+contacts.size()
        +" last rejection="+lastRejection+(rejectionTime==0?"":" ("+((System.nanoTime()-rejectionTime)/1_000_000_000L)+"s ago)"); }
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
        com.seibel.distanthorizons.core.util.math.DhVec3d origin=MinecraftRenderWrapper.INSTANCE.getCameraExactPosition();
        if(!snapshotCamera(input,Minecraft.getMinecraft().renderViewEntity,origin)) return;
        source=input;generation=buffers.getCoverageGeneration();valid=true;
    }
    /** Terrain renders before RenderManager refreshes viewerPos for this frame.
     * Pair the captured matrices with the current interpolated view, as missiles do.
     * Third-person/eye transforms are already in the model-view matrix.
     */
    boolean snapshotCamera(DhApiRenderParam input,net.minecraft.entity.Entity camera,
                           com.seibel.distanthorizons.core.util.math.DhVec3d origin) {
        if(camera==null || origin==null || !Float.isFinite(input.partialTicks)) return false;
        double x=camera.lastTickPosX+(camera.posX-camera.lastTickPosX)*input.partialTicks;
        double y=camera.lastTickPosY+(camera.posY-camera.lastTickPosY)*input.partialTicks;
        double z=camera.lastTickPosZ+(camera.posZ-camera.lastTickPosZ)*input.partialTicks;
        if(!Double.isFinite(x+y+z+origin.x+origin.y+origin.z)) return false;
        frame.update(input);nativeMvp.set(frame.mcProjectionMatrix);nativeMvp.multiply(frame.mcModelViewMatrix);
        nx=x;ny=y;nz=z;dx=origin.x;dy=origin.y;dz=origin.z;return true;
    }
    @SubscribeEvent public void start(TickEvent.RenderTickEvent event) {
        if(event.phase!=TickEvent.Phase.START) return;
        MarkerOverlayApi.reset();valid=false;sceneComplete=false;nativeDrawn.clear();nativeVehicles.clear();drawn=unknown=outside=unprepared=projectionRejected=captureRejected=0;
        models.clear();status="waiting for terrain view";
        Minecraft mc=Minecraft.getMinecraft();
        if(world!=mc.theWorld || connection!=mc.getNetHandler()) {
            clear();world=mc.theWorld;connection=mc.getNetHandler();sequence=-1;receipt=0;failed=false;lastRejection="none";rejectionTime=0;
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
        if(!drawing && valid && !shadow() && GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)==nativeFramebuffer) {
            nativeDrawn.add(event.entityPlayer.getUniqueID());
            if(owned()) {
                update(PlayerClient.INSTANCE.frames());Contact contact=contacts.get(event.entityPlayer.getUniqueID());
                if(contact!=null && contact.track.sample(System.nanoTime(),contact.sample)) {
                    float yaw=PlayerBodyAlignment.interpolate(event.entityPlayer.prevRenderYawOffset,event.entityPlayer.renderYawOffset,frame.partialTicks);
                    contact.bodyAlignment.apply(contact.sample.bodyYaw,contact.sample.headYaw,yaw,System.nanoTime());
                }
            }
        }
    }
    static int actorDepth() { return INSTANCE!=null && INSTANCE.sceneComplete?INSTANCE.scene.depthTexture():0; }
    static int actorColour() { return INSTANCE!=null && INSTANCE.sceneComplete?INSTANCE.scene.colourTexture():0; }
    private boolean vehiclesOwnLegacy() { return vehicles!=null && vehicles.ownsLegacy(); }
    private boolean primaryNativeView() {
        return !failed && !drawing && valid && !shadow() && world==Minecraft.getMinecraft().theWorld
            && GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)==nativeFramebuffer;
    }
    private boolean replaceVehicle(net.minecraft.entity.Entity entity) {
        return primaryNativeView() && !vehicles.localVehicle(entity) && vehicles.contains(entity) && !DistantSmokeRenderer.nativeModelVisible(entity,vehicles.radius(entity));
    }
    private boolean observedVehicle(net.minecraft.entity.Entity entity) {
        if(!primaryNativeView() || (!vehicles.localVehicle(entity) && !DistantSmokeRenderer.nativeModelVisible(entity,vehicles.radius(entity)))) return false;
        nativeVehicles.add(entity.getUniqueID());return true;
    }
    private boolean owned() { return PlayerClient.INSTANCE!=null && PlayerClient.INSTANCE.ownsLegacy(); }
    boolean cameraReady(Object expectedWorld) { return valid && !failed && world==expectedWorld && (owned() || vehiclesOwnLegacy()); }
    boolean cameraOwned() { return owned() || vehiclesOwnLegacy(); }
    boolean cameraLocalVehicle(net.minecraft.entity.Entity entity) { return vehicles.localVehicle(entity); }
    boolean cameraTerrainBegin(DhApiMat4f mvp,double x,double y,double z) {
        return terrain.begin(source,Minecraft.getMinecraft().theWorld,generation,mvp,x,y,z);
    }
    boolean cameraTerrainKnown(double cx,double cy,double cz,ActorModel model) {
        return terrain.isKnown(cx,cy,cz,model.x(),model.centerY(),model.z(),model.radius());
    }
    void cameraTerrainEnd() { terrain.clear(); }
    void cameraModels(List<ActorModel> output,Set<UUID> nativeIds) {
        if(owned()) {
            update(PlayerClient.INSTANCE.frames());
            long now=System.nanoTime();
            for(Map.Entry<UUID,Contact> entry:contacts.entrySet())
                if(!nativeIds.contains(entry.getKey()) && entry.getValue().track.sample(now,entry.getValue().sample)) output.add(entry.getValue());
        }
        vehicles.collect(output,nativeIds);
    }
    double cameraVehicleRadius(net.minecraft.entity.Entity entity) { return vehicles.available()?vehicles.radius(entity):Double.NaN; }
    void cameraDraw(ActorModel model,double x,double y,double z,DhApiMat4f view) throws Exception { drawModel(model,x,y,z,view); }
    MarkerOverlayApi.MarkerView markerView() {
        if(!valid || failed || world!=Minecraft.getMinecraft().theWorld) return null;
        IIrisAccessor iris=ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
        boolean shaders=iris!=null && iris.isShaderPackInUse();
        ShaderEffectsTarget target=shaders?ShaderEffectsTarget.current():null;
        if(shaders && target==null) return null;
        MarkerOverlayApi.MarkerView result=new MarkerOverlayApi.MarkerView();
        result.projection=frame.mcProjectionMatrix;result.view=frame.mcModelViewMatrix;
        result.nativeMvp.set(nativeMvp);if(!ModelProjection.inverse(nativeMvp,result.inverse)) return null;
        result.dhMvp.set(target==null?frame.dhProjectionMatrix:target.terrainProjection);
        if(target!=null) ShaderActorJitter.apply(result.dhMvp,target.jitterX,target.jitterY);
        result.dhMvp.multiply(frame.dhModelViewMatrix);
        DhApiMat4f translation=new DhApiMat4f();translation.m00=translation.m11=translation.m22=translation.m33=1;
        translation.m03=(float)(nx-dx);translation.m13=(float)(ny-dy);translation.m23=(float)(nz-dz);result.dhMvp.multiply(translation);
        result.x=nx;result.y=ny;result.z=nz;
        result.width=Minecraft.getMinecraft().getFramebuffer().framebufferWidth;result.height=Minecraft.getMinecraft().getFramebuffer().framebufferHeight;
        result.terrainDepth=target==null?com.seibel.distanthorizons.core.render.DhApiRenderProxy.activeOpenGlDhDepthTextureId:target.terrainDepth;
        result.nativeDepth=target==null?0:target.nativeSceneDepth;
        result.reverse=SingletonInjector.INSTANCE.get(AbstractDhRenderApiDefinition.class).getDepthDirection()==EDhApiDepthDirection.REVERSE_Z;
        return result.terrainDepth>0?result:null;
    }
    boolean markerKnown(MarkerOverlayApi.MarkerView view,double x,double y,double z,double radius) {
        return terrain.isKnown(view.x,view.y,view.z,x,y,z,Math.max(.5,radius));
    }
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
        if(terrainDepth<=0 || !terrain.begin(source,mc.theWorld,generation,nativeMvp,nx,ny,nz)) return;
        int width=target==null?mc.getFramebuffer().framebufferWidth:target.sceneWidth;
        int height=target==null?mc.getFramebuffer().framebufferHeight:target.sceneHeight;
        if(!ModelCapture.supportsSize(width,height)) { status="unsupported target "+width+"x"+height;terrain.clear();return; }
        status="preparing models";
        try {
            if(preparedWidth!=width || preparedHeight!=height) {
                capture.prepare(width,height);scene.prepare(width,height);preparedWidth=width;preparedHeight=height;
            }
            DhApiMat4f renderProjection=new DhApiMat4f();renderProjection.set(frame.mcProjectionMatrix);
            if(target!=null) ShaderActorJitter.apply(renderProjection,target.jitterX,target.jitterY);
            DhApiMat4f renderNativeMvp=new DhApiMat4f();renderNativeMvp.set(renderProjection);renderNativeMvp.multiply(frame.mcModelViewMatrix);
            dhMvp.set(target==null?frame.dhProjectionMatrix:target.terrainProjection);
            if(target!=null) ShaderActorJitter.apply(dhMvp,target.jitterX,target.jitterY);
            dhMvp.multiply(frame.dhModelViewMatrix);
            DhApiMat4f translation=new DhApiMat4f();translation.m00=translation.m11=translation.m22=translation.m33=1;translation.m03=(float)(nx-dx);translation.m13=(float)(ny-dy);translation.m23=(float)(nz-dz);
            dhMvp.multiply(translation);
            if(!ModelProjection.inverse(renderNativeMvp,inverseScene)) { status="invalid native projection";return; }
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
                if(!ModelBounds.project(renderNativeMvp,model.x()-nx,model.centerY()-ny,model.z()-nz,radius,width,height,bounds)) { outside++;continue; }
                if(!terrain.isKnown(nx,ny,nz,model.x(),model.centerY(),model.z(),radius)) {
                    unknown++;
                    if(now-rejectionTime>1_000_000_000L) {
                        rejectionTime=now;
                        lastRejection=terrain.missing()+" FOV="+String.format(java.util.Locale.ROOT,"%.2f",Math.toDegrees(2*Math.atan(1/frame.mcProjectionMatrix.m11)));
                    }
                    continue;
                }
                if(model.prepare()) visibleModels.add(model);else unprepared++;
            }
            if(visibleModels.isEmpty()) return;
            // Opaque pixels retain shared depth; alpha-bearing parts follow back-to-front actor ordering.
            visibleModels.sort((a,b)->Double.compare(distance(b),distance(a)));
            boolean completed=false;scene.begin(width,height,reverse);
            try {
                for(ActorModel model:visibleModels) {
                    double radius=model.radius();
                    if(!ModelBounds.project(renderNativeMvp,model.x()-nx,model.centerY()-ny,model.z()-nz,radius,width,height,bounds)) continue;
                    double x=model.x()-nx,y=model.y()-ny,z=model.z()-nz;
                    double eyeDepth=-(frame.mcModelViewMatrix.m20*x+frame.mcModelViewMatrix.m21*(model.centerY()-ny)+frame.mcModelViewMatrix.m22*z+frame.mcModelViewMatrix.m23);
                    if(!ModelProjection.prepare(renderProjection,eyeDepth,radius,localProjection)) { projectionRejected++;continue; }
                    localMvp.set(localProjection);localMvp.multiply(frame.mcModelViewMatrix);
                    if(!ModelProjection.inverse(localMvp,inverseLocal)) { projectionRejected++;continue; }
                    boolean modelComplete=false;
                    if(!capture.begin(width,height,bounds[0],bounds[1],bounds[2],bounds[3],localProjection)) { captureRejected++;continue; }
                    try { drawModel(model,x,y,z);modelComplete=true; }
                    finally { capture.end(modelComplete); }
                    drawn++;capture.composite(inverseLocal,dhMvp,renderNativeMvp,0,0,0,terrainDepth,target==null?0:target.nativeSceneDepth,reverse,true);
                }
                completed=true;
            } finally { scene.end(completed); }
            if(drawn==0) return;
            int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),oldDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            try {
                if(target!=null) bindTarget(target);
                scene.composite(inverseScene,dhMvp,renderNativeMvp,frame.mcModelViewMatrix,terrainDepth,target==null?0:target.nativeSceneDepth,shaders,clouds.texture,clouds.distance);
                if(target!=null) {
                    GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,target.opaqueSceneDepth,0);
                    GL11.glDrawBuffer(GL11.GL_NONE);
                    if(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Incomplete opaque actor depth target");
                    scene.compositeOpaqueDepth(inverseScene,dhMvp,renderNativeMvp,frame.mcModelViewMatrix,terrainDepth,target.nativeSceneDepth,clouds.texture,clouds.distance);
                }
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
        drawModel(model,x,y,z,frame.mcModelViewMatrix);
    }
    private void drawModel(ActorModel model,double x,double y,double z,DhApiMat4f view) throws Exception {
        float partial=model.partial();
        AutoCloseable isolated=FmurPlayerBridge.beginModelCapture();
        int mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);float lightX=OpenGlHelper.lastBrightnessX,lightY=OpenGlHelper.lastBrightnessY;
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();drawing=true;
        try {
            load(view);GL20.glUseProgram(0);
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
            if(isolated!=null) isolated.close();
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
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT2,GL11.GL_TEXTURE_2D,target.actorNormal,0);
        java.nio.IntBuffer outputs=BufferUtils.createIntBuffer(3);outputs.put(GL30.GL_COLOR_ATTACHMENT0);
        if(target.particleMaterial>0) outputs.put(GL30.GL_COLOR_ATTACHMENT1);
        if(target.actorNormal>0) outputs.put(GL30.GL_COLOR_ATTACHMENT2);outputs.flip();GL20.glDrawBuffers(outputs);
        if(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Incomplete actor output target");
    }
    private void clearPlayers() { for(Contact contact:contacts.values()) contact.close();contacts.clear(); }
    private void clear() { cameraPass.reset();clearPlayers();vehicles.clear();terrain.clear(); }
    private final class Contact implements ActorModel {
        final UUID uuid;
        float partial;
        Contact(UUID uuid) { this.uuid=uuid; }
        final PlayerTrack track=new PlayerTrack();final PlayerTrack.Sample sample=new PlayerTrack.Sample();
        final PlayerBodyAlignment bodyAlignment=new PlayerBodyAlignment();
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
                if(actor==null || actor.getEntityId()!=sample.entityId) bodyAlignment.reset();
                PlayerAppearance.Decoded decoded=appearance.appearance();if(actor!=null) actor.close();
                actor=new VisualPlayerActor(Minecraft.getMinecraft().theWorld,decoded.profile,sample.entityId);
                actor.appearance(decoded,fmur);revision=sample.revision;
            }
            actor.support(fmur);partial=actor.pose(sample);
            net.minecraft.entity.Entity tracked=Minecraft.getMinecraft().theWorld.getEntityByID(sample.entityId);
            Float nativeBody=null;
            if(tracked instanceof net.minecraft.entity.player.EntityPlayer && !tracked.isDead && tracked.getUniqueID().equals(uuid)) {
                net.minecraft.entity.player.EntityPlayer player=(net.minecraft.entity.player.EntityPlayer)tracked;
                nativeBody=PlayerBodyAlignment.interpolate(player.prevRenderYawOffset,player.renderYawOffset,frame.partialTicks);
            }
            actor.prevRenderYawOffset=actor.renderYawOffset=bodyAlignment.apply(sample.bodyYaw,sample.headYaw,nativeBody,System.nanoTime());
            return true;
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
