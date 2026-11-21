from pathlib import Path
import argparse,hashlib,subprocess,os,zipfile
p=argparse.ArgumentParser();p.add_argument('--tree-root',type=Path,required=True);p.add_argument('--output-dir',type=Path,required=True);a=p.parse_args()
B=a.tree_root.resolve();F=a.output_dir.resolve();F.mkdir(parents=True,exist_ok=True)
plugin=B/'external/kotlin-compose-compiler/org/jetbrains/kotlin/kotlin-compose-compiler-plugin/2.2.0/kotlin-compose-compiler-plugin-2.2.0.jar'
assert hashlib.sha256(plugin.read_bytes()).hexdigest()=='471e82ec9809643c41b3b849fe91ed3182d1c50e7cca6aa1943d6fd7d5427798','Unsupported upstream plugin version'
source=Path(__file__).with_name('Stability.kt');classes=F/'classes';classes.mkdir(exist_ok=True)
cmd=[str(B/'external/kotlinc/bin/kotlinc'),str(source),'-classpath',str(plugin)+':'+str(B/'external/kotlinc/lib/kotlin-compiler.jar'),'-Xfriend-paths='+str(plugin),'-jvm-target','1.8','-module-name','kotlin-compose-compiler-hosted','-d',str(classes)]
env=os.environ.copy();env['JAVA_OPTS']='-Xmx2048M';r=subprocess.run(cmd,cwd=B,env=env,capture_output=True,text=True,timeout=180);(F/'compile.log').write_text(r.stdout+r.stderr);print(r.stdout+r.stderr,flush=True);assert r.returncode==0,r.returncode
name='androidx/compose/compiler/plugins/kotlin/analysis/StabilityInferencer.class';out=F/'kotlin-compose-compiler-plugin-2.2.0-cache.jar';changed=0
with zipfile.ZipFile(plugin) as original,zipfile.ZipFile(out,'w') as target:
 for info in original.infolist():
  data=original.read(info)
  if info.filename==name:data=(classes/name).read_bytes();changed+=1
  target.writestr(info,data)
assert changed==1
for tag,p in [('before',plugin),('after',out)]:
 r=subprocess.run([str(B/'prebuilts/jdk/jdk21/linux-x86/bin/javap'),'-classpath',str(p),'-public','androidx.compose.compiler.plugins.kotlin.analysis.StabilityInferencer'],capture_output=True,text=True,check=True);(F/(tag+'-public-api.txt')).write_text(r.stdout)
assert (F/'before-public-api.txt').read_text()==(F/'after-public-api.txt').read_text()
with zipfile.ZipFile(plugin) as a,zipfile.ZipFile(out) as b:
 assert a.namelist()==b.namelist();diff=[n for n in a.namelist() if a.read(n)!=b.read(n)];assert diff==[name],diff
print('REPRODUCIBLE_ONE_CLASS_COMPOSE_CACHE_BACKPORT_PASS',out)
