from pathlib import Path
import os,subprocess,zipfile,shutil,hashlib,json
project=Path(__file__).resolve().parent
sdk=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
if not sdk:raise SystemExit('Set ANDROID_HOME to an Android SDK with platform/build-tools 34.')
sdk=Path(sdk)
bin=sdk/'build-tools/34.0.0'
android=sdk/'platforms/android-34/android.jar'
java_home=os.environ.get('JAVA_HOME')
java=Path(java_home)/'bin' if java_home else Path(shutil.which('javac') or '').parent
android_user=Path(os.environ.get('ANDROID_USER_HOME',str(Path.home()/'.android')))
keystore=android_user/'debug.keystore'
if not keystore.is_file():raise SystemExit('Build the launcher first to generate the standard Android debug keystore.')
build=project/'build';build.mkdir(exist_ok=True)
for name in ['classes','shell-classes','dex','shell-dex']:
    path=build/name
    if path.exists():shutil.rmtree(path)
    path.mkdir()
def run(args):subprocess.run([str(a) for a in args],check=True)
run([bin/'aapt2','compile','--dir',project/'res','-o',build/'resources.zip'])
run([bin/'aapt2','link','-I',android,'--manifest',project/'AndroidManifest.xml','-o',build/'base.apk',build/'resources.zip'])
run([java/'javac','-source','8','-target','8','-classpath',android,'-d',build/'classes',*project.glob('src/**/*.java')])
run([java/'jar','cf',build/'classes.jar','-C',build/'classes','.'])
run([bin/'d8','--lib',android,'--min-api','26','--output',build/'dex',build/'classes.jar'])
shutil.copy2(build/'base.apk',build/'unsigned.apk')
with zipfile.ZipFile(build/'unsigned.apk','a') as archive:archive.write(build/'dex/classes.dex','classes.dex')
run([bin/'zipalign','-f','4',build/'unsigned.apk',build/'aligned.apk'])
run([bin/'apksigner','sign','--ks',keystore,'--ks-pass','pass:android','--key-pass','pass:android','--out',build/'SonyAppsLayoutTrial.apk',build/'aligned.apk'])
run([bin/'apksigner','verify',build/'SonyAppsLayoutTrial.apk'])
run([java/'javac','-source','8','-target','8','-classpath',android,'-d',build/'shell-classes',project/'shell/LayoutCtl.java'])
run([java/'jar','cf',build/'shell-classes.jar','-C',build/'shell-classes','.'])
run([bin/'d8','--lib',android,'--min-api','26','--output',build/'shell-dex',build/'shell-classes.jar'])
shutil.copy2(build/'shell-dex/classes.dex',build/'layoutctl.dex')
print(json.dumps({f.name:{'bytes':f.stat().st_size,'sha256':hashlib.sha256(f.read_bytes()).hexdigest()} for f in [build/'SonyAppsLayoutTrial.apk',build/'layoutctl.dex']}))
