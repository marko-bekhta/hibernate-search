from pathlib import Path
import subprocess,tempfile
import sys
base=Path(__file__).resolve().parents[2]
classpath=(base/'util/json/target/hibernate-search-util-json-9.0.0-SNAPSHOT.jar').as_posix()+':'+Path(sys.argv[1]).read_text().strip()
with tempfile.TemporaryDirectory(prefix='json-task2-jpms-') as directory:
 directory=Path(directory); source=directory/'src'; (source/'smoke').mkdir(parents=True)
 (source/'module-info.java').write_text('module smoke { requires org.hibernate.search.util.json; }\n')
 main=source/'smoke/Main.java'
 main.write_text('''package smoke;
import org.hibernate.search.util.json.JsonObject;
import org.hibernate.search.util.json.spi.JsonParsing;
import org.hibernate.search.util.json.spi.JsonAccessor;
public class Main {
 public static void main(String[] args) {
  JsonObject tree = new JsonParsing().parse("{\\\"hits\\\":{\\\"value\\\":9007199254740993}}").getAsJsonObject();
  long value = JsonAccessor.root().property("hits").property("value").asLong().get(tree).orElseThrow();
  if (value != 9007199254740993L) throw new AssertionError(value);
 }
}
''')
 out=directory/'classes'; args=['javac','--release','17','--module-path',classpath,'-d',str(out),str(source/'module-info.java'),str(main)]
 subprocess.run(args,check=True)
 subprocess.run(['java','--module-path',classpath+':'+str(out),'-m','smoke/smoke.Main'],check=True)
 print('Public model/SPI named-module compilation and runtime smoke: PASS')
 main.write_text('package smoke; import org.hibernate.search.util.json.impl.JacksonJsonParserFactory; public class Main { JacksonJsonParserFactory implementation; }')
 rejected=subprocess.run(args,text=True,capture_output=True)
 if rejected.returncode==0 or 'does not export' not in rejected.stderr: raise AssertionError(rejected.stderr)
 print('Implementation import rejected for external named module: PASS')
 print(rejected.stderr.strip())
 subprocess.run(['jar','--describe-module','--file',str(base/'util/json/target/hibernate-search-util-json-9.0.0-SNAPSHOT.jar')],check=True)

# The probe uses pinned sniffer bytecode on the freshly resolved runtime classpath.
clients_classpath = Path(sys.argv[2]).read_text().strip()
with tempfile.TemporaryDirectory(prefix='json-jackson3-sniffers-') as directory:
 directory=Path(directory)
 probe=Path(__file__).with_name('SnifferCoexistenceProbe.java')
 subprocess.run(['javac','--release','17','-cp',clients_classpath,'-d',str(directory),str(probe)],check=True)
 subprocess.run(['java','-ea','-cp',clients_classpath+':'+str(directory),'SnifferCoexistenceProbe'],check=True)
