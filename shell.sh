#!/bin/sh
here=`dirname $0`
if [ "$1" = "update" ]; then
    snapshot=`wget -nv -q -O- http://contd.cleo.com/nexus/content/repositories/snapshots/com/cleo/labs/cleo-labs-util/0.0.1-SNAPSHOT/maven-metadata.xml | sed -n '0,/<value>/s/.*<value>\(.*\)<\/value>/\1/p'`
    wget -nv -q -O $here/cleo-labs-util-0.0.1-SNAPSHOT.jar http://contd.cleo.com/nexus/content/repositories/snapshots/com/cleo/labs/cleo-labs-util/0.0.1-SNAPSHOT/cleo-labs-util-$snapshot.jar
    snapshot=`wget -nv -q -O- http://contd.cleo.com/nexus/content/repositories/snapshots/com/cleo/labs/cleo-labs-api-shell/0.0.1-SNAPSHOT/maven-metadata.xml | sed -n '0,/<value>/s/.*<value>\(.*\)<\/value>/\1/p'`
    wget -nv -q -O $here/cleo-labs-api-shell-0.0.1-SNAPSHOT.jar http://contd.cleo.com/nexus/content/repositories/snapshots/com/cleo/labs/cleo-labs-api-shell/0.0.1-SNAPSHOT/cleo-labs-api-shell-$snapshot.jar
else
    service='cleo-harmony'
    if [ -e "/etc/init/$1.conf" ]; then
        service=$1
        shift
    fi
    harmony=`sed -n '/^env\s*CLEOHOME=/s/.*=\s*//p' /etc/init/$service.conf`
    unset DISPLAY
    classpath=$here/cleo-labs-api-shell-0.0.1-SNAPSHOT.jar:$here/cleo-labs-util-0.0.1-SNAPSHOT.jar:$harmony/lib/\*:$harmony/lib/help/\*:$harmony/webserver/AjaxSwing/lib/ajaxswing.jar:$harmony/lib/hibernate/\*:$harmony/lib/secureshare/\*:$harmony/lib/json/\*:$harmony/lib/ext/\*:$harmony/lib/uri/\*
    (cd $harmony; ./jre/bin/java -cp $classpath com.cleo.labs.api.shell.Shell -h . -p h -m client "$@")
fi
