#!/usr/bin/env python3
"""Beauty stills rendered FROM the shipped GLBs. VTK is authoring-only, not in the app.
No replacement geometry, downloaded HDRIs, fonts or external textures.
python -m pip install vtk==9.6.2 numpy Pillow
VTK_DEFAULT_OPENGL_WINDOW=vtkEGLRenderWindow python tools/atelier/render_posters.py
"""
from __future__ import annotations
import os
os.environ.setdefault('VTK_DEFAULT_OPENGL_WINDOW', 'vtkEGLRenderWindow')
import hashlib
import json
from pathlib import Path
import numpy as np
import vtk
from vtk.util.numpy_support import numpy_to_vtk
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
MODELS = ROOT / 'app-android/src/main/assets/atelier/models'
OUT = ROOT / 'app-android/src/main/assets/atelier/cinematic'


def environment():
    """Original linear-light equirectangular studio, deterministic and entirely local."""
    w,h=512,256
    lon=np.linspace(-np.pi,np.pi,w);lat=np.linspace(-np.pi/2,np.pi/2,h)
    x=np.cos(lat[:,None])*np.sin(lon[None,:]);y=np.broadcast_to(np.sin(lat[:,None]),(h,w));z=np.cos(lat[:,None])*np.cos(lon[None,:])
    d=np.stack([x,y,z],axis=-1)
    e=np.ones((h,w,3))*(.10+.20*(y[...,None]+1)/2)*np.array([1.,1.05,.94])
    for direction,color,strength,power in [((-1,1.3,2),(1.,.88,.72),8,24),((1,.6,-1),(.73,.88,1.),6,18),((.2,2,.1),(1.,1.,.85),5,12)]:
        v=np.array(direction);v=v/np.linalg.norm(v)
        e+=np.maximum(0,np.sum(d*v,axis=-1))[...,None]**power*np.array(color)*strength
    arr=numpy_to_vtk(e.astype(np.float32).reshape(-1,3),deep=True)
    im=vtk.vtkImageData();im.SetDimensions(w,h,1);im.GetPointData().SetScalars(arr)
    tex=vtk.vtkTexture();tex.SetInputData(im);tex.SetColorModeToDirectScalars();tex.InterpolateOn();tex.MipmapOn()
    return tex


def render(model,theme,name,focus,eye_offset,scale,size=(1600,1000),roll=0):
    rw=vtk.vtkRenderWindow();rw.SetOffScreenRendering(1);rw.SetSize(*size);rw.SetMultiSamples(8)
    importer=vtk.vtkGLTFImporter();importer.SetFileName(str(MODELS/(model+'.glb')));importer.SetRenderWindow(rw);importer.Update()
    r=rw.GetRenderers().GetFirstRenderer();r.AutomaticLightCreationOff();r.GradientBackgroundOn()
    if theme=='dark':
        r.SetBackground(.030,.053,.043);r.SetBackground2(.095,.13,.10)
    else:
        r.SetBackground(.86,.87,.82);r.SetBackground2(.965,.956,.926)
    env=environment();r.SetEnvironmentTexture(env,False);r.UseImageBasedLightingOn()
    for pos,color,power in [((-2,3,4),(1.,.88,.72),2.4),((3,2,-3),(.62,.82,1.),1.9),((-3,1,-2),(.9,1.,.83),1.3)]:
        light=vtk.vtkLight();light.SetPosition(*pos);light.SetFocalPoint(*focus);light.SetColor(*color);light.SetIntensity(power);r.AddLight(light)
    camera=r.GetActiveCamera();camera.SetPosition(*(np.array(focus)+np.array(eye_offset)));camera.SetFocalPoint(*focus);camera.SetViewUp(0,1,0);camera.ParallelProjectionOn();camera.SetParallelScale(scale);camera.Roll(roll)
    r.ResetCameraClippingRange()
    steps=vtk.vtkRenderStepsPass();tone=vtk.vtkToneMappingPass();tone.SetToneMappingType(vtk.vtkToneMappingPass.GenericFilmic);tone.SetExposure(.8);tone.SetDelegatePass(steps);r.SetPass(tone)
    rw.Render()
    capture=vtk.vtkWindowToImageFilter();capture.SetInput(rw);capture.ReadFrontBufferOff();capture.SetInputBufferTypeToRGB();capture.Update()
    tmp=OUT/(name+'.png');writer=vtk.vtkPNGWriter();writer.SetFileName(str(tmp));writer.SetInputConnection(capture.GetOutputPort());writer.Write()
    with Image.open(tmp) as im: im.save(OUT/(name+'.jpg'),quality=91,optimize=True,subsampling=0)
    tmp.unlink();rw.Finalize()
    path=OUT/(name+'.jpg')
    print(name,path.stat().st_size,flush=True)
    return {'file':path.name,'bytes':path.stat().st_size,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'source':model+'.glb','sourceSha256':hashlib.sha256((MODELS/(model+'.glb')).read_bytes()).hexdigest(),'camera':{'focus':focus,'eyeOffset':eye_offset,'parallelScale':scale,'rollDegrees':roll},'dimensions':size,'kind':'render of original committed geometry','theme':theme}


def main():
    OUT.mkdir(parents=True,exist_ok=True);result=[]
    for theme in ['dark','light']:
        result.append(render('recurve',theme,'recurve-'+theme,(-.20,.075,0),(.9,.32,3),.35,(1600,760),-10))
        result.append(render('recurve',theme,'recurve-full-'+theme,(.13,0,0),(.95,.22,3),.95,(1200,1400)))
        result.append(render('arrow',theme,'arrow-'+theme,(0,0,0),(.35,.42,3),.22,(1600,760),-12))
        result.append(render('target',theme,'target-'+theme,(-.19,.66,0),(.85,.42,3),.76,(1440,1000)))
    for model,name,focus,eye,scale,size,roll in [
        ('recurve','recurve-riser-detail',(-.01,.16,0),(.6,.12,2),.24,(1100,1100),-12),
        ('recurve','recurve-grip-detail',(-.01,-.022,0),(-.85,.12,2),.084,(1100,1100),-12),
        ('recurve','recurve-sight-detail',(.18,.17,-.06),(1.,.3,2),.14,(1440,960),0),
        ('arrow','arrow-vanes-detail',(-.315,0,0),(.1,.6,2),.041,(1440,960),-16),
        ('arrow','arrow-nock-detail',(-.380,0,0),(-1,.25,2),.022,(1100,1100),-12),
    ]:
        result.append(render(model,'dark',name,focus,eye,scale,size,roll))
    (OUT/'manifest.json').write_text(json.dumps({'renderer':'VTK 9.6.2 / EGL / PBR','images':result,'noGeneratedAnatomyClaims':True},indent=2)+'\n')

if __name__=='__main__': main()
