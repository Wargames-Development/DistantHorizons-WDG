#!/usr/bin/env python3
from zipfile import ZipFile
from pathlib import Path
import struct,math,json
import argparse
parser=argparse.ArgumentParser(description='Read-only camera/body reconstruction from a Forge 1.7.10 DH relay recording. Positions are quantized packet observations, not captured GL matrices.')
parser.add_argument('recording',type=Path)
parser.add_argument('--json',type=Path)
parser.add_argument('--player-id',type=int)
parser.add_argument('--fov',type=float,default=84)
parser.add_argument('--aspect',type=float,default=1.6)
args=parser.parse_args();p=args.recording
z=ZipFile(p);b=z.read('recording.tmcpr');i=0;poses=[];samples=[];pos=[9051.3,358.97,-2334.03];yaw=0;pitch=0;selfid=args.player_id
while i<len(b):
 t,n=struct.unpack_from('>ii',b,i);i+=8;d=b[i:i+n];i+=n
 if d[0]==12 and selfid is None:
  value=0;j=1
  while True:
   v=d[j];value|=(v&127)<<((j-1)*7);j+=1
   if v<128:break
  selfid=value
 if d[0]==24 and struct.unpack_from('>i',d,1)[0]==selfid:
  pos=list(struct.unpack_from('>iii',d,5));pos=[a/32 for a in pos];yaw,pitch=[a*360/256 for a in struct.unpack_from('>bb',d,17)];poses.append((t,*pos,yaw,pitch))
 if d[0]==23 and struct.unpack_from('>i',d,1)[0]==selfid:
  dx,dy,dz,y,pit=struct.unpack_from('>bbbbb',d,5);pos=[v+delta/32 for v,delta in zip(pos,[dx,dy,dz])];yaw=y*360/256;pitch=pit*360/256;poses.append((t,*pos,yaw,pitch))
 if d[0]!=63:continue
 l=d[1];channel=d[2:2+l].decode(errors='replace')
 if channel!='DHVisualTrails':continue
 raw=d[4+l:]
 if raw[0]!=1:continue
 k=1;version,dim,count=struct.unpack_from('>BiB',raw,k);k+=6
 for _ in range(count):
  family,eid,slot,num=struct.unpack_from('>BiBB',raw,k);k+=7;k+=16;tick=struct.unpack_from('>q',raw,k)[0];k+=8
  length=raw[k];k+=1;style=raw[k:k+length].decode();k+=length
  xyz=struct.unpack_from('>dddddd',raw,k);k+=48;props=struct.unpack_from('>ffff',raw,k);k+=16
  if style.startswith('dhBody') or style=='dhNativeBlast':
   dx,dy,dz=[v-q for v,q in zip(xyz[3:],[pos[0],pos[1]+1.62,pos[2]])]
   y=math.radians(yaw);pit=math.radians(pitch)
   w=-math.sin(y)*math.cos(pit)*dx-math.sin(pit)*dy+math.cos(y)*math.cos(pit)*dz
   right=math.cos(y)*dx+math.sin(y)*dz
   up=-math.sin(y)*math.sin(pit)*dx+math.cos(pit)*dy+math.cos(y)*math.sin(pit)*dz
   ndc=[right/w/(math.tan(math.radians(args.fov/2))*args.aspect),up/w/math.tan(math.radians(args.fov/2))] if w else [0,0]
   row=dict(t=t,tick=tick,type=style,xyz=xyz[3:],camera=[*pos,yaw,pitch],w=w,ndc=ndc,props=props)
   samples.append(row)
if args.json: args.json.write_text(json.dumps(samples,indent=2))
for row in samples:
 if row['t']>30000 and (row['t']//500 != samples[max(0,samples.index(row)-1)]['t']//500 or row['type']=='dhNativeBlast'):
  print(f"t={row['t']} tick={row['tick']} {row['type']} xyz={tuple(round(x,2) for x in row['xyz'])} camera={tuple(round(x,2) for x in row['camera'])} depth={row['w']:.1f} NDC={tuple(round(x,3) for x in row['ndc'])}")
