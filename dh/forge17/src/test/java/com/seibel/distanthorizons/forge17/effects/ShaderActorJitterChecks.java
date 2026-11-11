package com.seibel.distanthorizons.forge17.effects;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
public final class ShaderActorJitterChecks {
    public static int run() {
        String body="vec2 offset = jitterOffsets[int(framemod8)] * (w / vec2(viewWidth, viewHeight)); return coord + offset;";
        require(ShaderActorJitter.parseStrength(body)==1,"full compiled jitter");
        require(ShaderActorJitter.parseStrength(body.replace("return coord", "offset *= 0.125; return coord"))==.125f,"small compiled jitter");
        require(ShaderActorJitter.parseStrength("#define TAA_JITTER 2\n"+body)==.33f,"unexpanded configured jitter");
        require(ShaderActorJitter.parseStrength("#define TAA_JITTER 0\n"+body)==0,"disabled jitter");
        require(ShaderActorJitter.parseStrength("return coord;")==0,"shader without jitter");
        String table="vec2 jitterOffsets[8] = vec2[8](vec2(.125,-.375),vec2(-.125,.375),vec2(.625,.125),vec2(.375,-.625),vec2(-.625,.625),vec2(-.875,-.125),vec2(.375,-.875),vec2(.875,.875));";
        require(ShaderActorJitter.matchingOffsets(table),"option-preprocessed terrain offsets are recognised");
        require(!ShaderActorJitter.matchingOffsets(table.replace(".875,.875",".5,.5")),"different jitter sequence cannot be mistaken for this pack");
        int checks=7;
        for(float fov:new float[]{10,30,70,110}) for(int frame=0;frame<8;frame++) for(int step=0;step<32;step++) {
            DhApiMat4f p=new DhApiMat4f();float sy=(float)(1/Math.tan(Math.toRadians(fov)*.5));
            p.m00=sy/1.7f;p.m11=sy;p.m22=-1.01f;p.m23=-.101f;p.m32=-1;
            float x=(frame-3.5f)/1920,y=(3.5f-frame)/1080;
            double wx=12-step*.1,wy=4-step*.02,wz=-200-step;
            double beforeX=(p.m00*wx)/-wz,beforeY=(p.m11*wy)/-wz;
            ShaderActorJitter.apply(p,x,y);
            double afterX=(p.m00*wx+p.m02*wz)/-wz,afterY=(p.m11*wy+p.m12*wz)/-wz;
            require(Math.abs(afterX-beforeX-x)<1e-7 && Math.abs(afterY-beforeY-y)<1e-7,"moving and zoomed projection receives the shader's exact clip displacement");checks++;
        }
        return checks;
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
