cd $(dirname $0)
set -e

function findJar() {
    find target -name feng-*.jar | head -1
}
jar=$(findJar)
if [[ -z "$jar" ]]; then
    mvn package -DskipTests >/dev/null
    jar=$(findJar)
fi

cmd="java -Dfeng.asan=true -jar ${jar}"
args="-Lstd=std -p test -t f -b m -D -T"

out="target/test-std"
mkdir -p "$out"

function cleanup() {
    rm -rf ${out}
}
trap cleanup EXIT

function test_all() {
  i=1
  ls tests | while read t; do
      if [ "${t##*.}" != "feng" ]; then
          continue
      fi
      td="${out}/tmp-${i}"
      echo "=== testing ${t} ==="
      mkdir -p ${td}
      $cmd $args -i tests/${t} -o ${td}
      ${td}/test
      i=$((i+1))
  done
}

function test_one() {
    c=$1
    f=$c
    if [ "${c##*.}" != "feng" ]; then
        f="${f}.feng"
    fi
    td="${out}/tmp-${c}"
    mkdir -p ${td}
    $cmd $args -i tests/${f} -o ${td}
    ${td}/test
}

if [[ -z "${1}" ]]; then
    test_all
else
    test_one "${1}"
fi