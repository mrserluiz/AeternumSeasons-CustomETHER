#!/usr/bin/env python3
"""Isolated Paper startup, climate reference and save/restart checks."""
import hashlib,json,pathlib,shutil,subprocess,time,urllib.request,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
SERVER=ROOT/'aeternum-build/target/runtime-smoke'
SERVER.mkdir(parents=True,exist_ok=True)
def fetch(url):
    req=urllib.request.Request(url,headers={'User-Agent':'Aeternum-CI/1.0 (https://github.com/mrserluiz/AeternumSeasons-CustomETHER)'})
    with urllib.request.urlopen(req,timeout=90) as r:return r.read()
builds=json.loads(fetch('https://fill.papermc.io/v3/projects/paper/versions/26.2/builds'))
build=next(b for b in builds if b['id']==133)
download=build['downloads']['server:default']
binary=fetch(download['url'])
assert hashlib.sha256(binary).hexdigest()==download['checksums']['sha256']
(SERVER/'paper.jar').write_bytes(binary)
plugins=SERVER/'plugins';plugins.mkdir(exist_ok=True)
shutil.copy(ROOT/'aeternum-build/target/AeternumSeasons-4.5.2-CLIMATE-API-BETA.jar',plugins)
data=plugins/'AeternumSeasons';data.mkdir(exist_ok=True)
(data/'config.yml').write_text('features:\n  portals:\n    frost:\n      enabled: false\n    heat:\n      enabled: false\n')
(data/'climate.yml').write_text('biome_spoof:\n  enabled: true\nworld_climate:\n  profiles:\n    world:\n      enabled: true\n      season: WINTER\n      climate-biome: minecraft:snowy_plains\n')
cp=(ROOT/'aeternum-build/target/compile-classpath.txt').read_text().strip()+':'+str(ROOT/'aeternum-build/target/classes')
classes=ROOT/'aeternum-build/target/probe-classes';classes.mkdir(exist_ok=True)
subprocess.run(['javac','-cp',cp,'-d',str(classes),str(ROOT/'tools/ClimateProbe.java')],check=True)
(classes/'plugin.yml').write_text('name: ClimateProbe\nversion: 1\nmain: probe.ClimateProbe\napi-version: "26.2"\ndepend: [AeternumSeasons]\n')
subprocess.run(['jar','--create','--file',str(plugins/'ClimateProbe.jar'),'-C',str(classes),'.'],check=True)
(SERVER/'eula.txt').write_text('eula=true\n')
(SERVER/'server.properties').write_text('online-mode=false\nview-distance=2\nsimulation-distance=2\nlevel-type=minecraft:flat\nmax-players=1\n')
for attempt in (1,2):
    log=SERVER/f'run-{attempt}.log'
    with log.open('w') as out:
        process=subprocess.Popen(['java','-Xmx2G','-jar','paper.jar','--nogui'],cwd=SERVER,stdin=subprocess.PIPE,stdout=out,stderr=subprocess.STDOUT,text=True)
        try:
            process.wait(timeout=240)
        except subprocess.TimeoutExpired:
            process.kill();process.wait()
            raise RuntimeError('Paper startup timed out; inspect '+str(log))
    text=log.read_text()
    if 'AETERNUM_CLIMATE_RUNTIME_OK' not in text or 'AETERNUM_CLIMATE_RUNTIME_FAILED' in text:
        print(text[-24000:]);raise RuntimeError('Climate runtime check failed')
print('AETERNUM_SAVE_RESTART_OK')

