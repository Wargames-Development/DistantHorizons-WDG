package com.seibel.distanthorizons.forge17.effects;

import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.*;
import java.net.*;
import java.util.zip.ZipFile;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.Type;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.FieldNode;

/** Run the real native coverage query with each supported Angelica class inventory. */
final class NativeCoverageChecks {
    private static void set(Object object,String name,Object value) throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);
    }
    static int run(String jar) {
        String name="com.seibel.distanthorizons.forge17.actors.AngelicaTerrainCoverage";
        String packaged=System.getProperty("dh.effects.packagedCoverageFixture");
        try(ZipFile angelica=new ZipFile(jar);
            URLClassLoader loader=new URLClassLoader(new URL[]{new File(jar).toURI().toURL(),
            new File(packaged==null?".":packaged).toURI().toURL(),
            ActorLifecycleChecks.class.getProtectionDomain().getCodeSource().getLocation(),
            com.seibel.distanthorizons.forge17.actors.ModelBounds.class.getProtectionDomain().getCodeSource().getLocation()},NativeCoverageChecks.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String requested,boolean resolve) throws ClassNotFoundException {
                if(requested.equals(name) || requested.startsWith("org.embeddedt.embeddium.")
                        || requested.startsWith("DistantHorizons.libraries.unimi.dsi.fastutil.")) {
                    Class<?> type=findLoadedClass(requested);if(type==null) type=findClass(requested);if(resolve) resolveClass(type);return type;
                }
                return super.loadClass(requested,resolve);
            }
        }) {
            Class<?> type=loader.loadClass(name);Object coverage=type.newInstance();
            if(packaged!=null) require(type.getProtectionDomain().getCodeSource().getLocation().equals(new File(packaged).toURI().toURL()),
                "coverage fixture loaded the packaged mod");
            Field access=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);
            sun.misc.Unsafe allocator=(sun.misc.Unsafe)access.get(null);
            Object world=allocator.allocateInstance(net.minecraft.client.multiplayer.WorldClient.class);
            set(coverage,"world",world);set(coverage,"minSection",0);set(coverage,"maxSection",15);
            Long2ReferenceMap<Object> sections=new Long2ReferenceOpenHashMap<>();
            String manager="org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager.class";
            ClassNode node=new ClassNode();
            try(InputStream bytes=angelica.getInputStream(angelica.getEntry(manager))) {
                new ClassReader(bytes).accept(node,ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
            FieldNode field=node.fields.stream().filter(f -> f.name.equals("sectionByPosition")).findFirst().get();
            Class<?> indexType=loader.loadClass(Type.getType(field.desc).getClassName());
            require(indexType.isInstance(sections),"uses Angelica's map type");
            Method resolve=type.getDeclaredMethod("primitiveLookup",Class.class);resolve.setAccessible(true);
            set(coverage,"sectionLookup",resolve.invoke(null,indexType));
            set(coverage,"nativeSections",sections);
            Class<?> position=loader.loadClass("org.embeddedt.embeddium.impl.util.PositionUtil");
            long key=(Long)position.getMethod("packSection",int.class,int.class,int.class).invoke(null,0,0,-8);
            Class<?> section=loader.loadClass("org.embeddedt.embeddium.impl.render.chunk.RenderSection");
            Object built=allocator.allocateInstance(section);
            set(built,"contextData",section.getField("EMPTY_DATA").get(null));
            sections.put(key,built);
            DhApiMat4f projection=new DhApiMat4f();projection.m00=projection.m11=30;projection.m32=-1;projection.m22=-1.01F;projection.m23=-.1F;
            Method query=type.getMethod("isKnown",net.minecraft.world.World.class,int.class,int.class,int.class,int.class,
                DhApiMat4f.class,double.class,double.class,double.class);
            require(Boolean.TRUE.equals(query.invoke(coverage,world,0,0,32,-128,projection,0.,0.,0.)),"offscreen vertical sections cannot erase covered vehicle pixels");
            sections.clear();
            require(Boolean.TRUE.equals(query.invoke(coverage,world,0,0,32,-128,projection,0.,0.,0.)),"section status is reused within the same view");
            type.getMethod("clear").invoke(coverage);
            set(coverage,"world",world);set(coverage,"nativeSections",sections);
            require(Boolean.FALSE.equals(query.invoke(coverage,world,0,0,32,-128,projection,0.,0.,0.)),"visible native terrain gap remains unknown");
            require(Boolean.TRUE.equals(query.invoke(coverage,world,0,16,32,-128,projection,0.,0.,0.)),"wholly offscreen queried section needs no scene depth");
            require(Boolean.FALSE.equals(query.invoke(coverage,null,0,16,32,-128,projection,0.,0.,0.)),"wrong world cannot authorize coverage");
            return 6;
        } catch(Exception error) { throw new AssertionError("Native coverage regression: "+jar,error); }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
