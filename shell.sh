#!/bin/sh
unset DISPLAY
harmony=`sed -n '/^env\s*CLEOHOME=/s/.*=\s*//p' /etc/init/cleo-harmony.conf`
here=`dirname $0`
$harmony/jre/bin/java -cp $here/cc-shell.jar:$harmony/lib/\*:$harmony/lib/help/\*:$harmony/webserver/AjaxSwing/lib/ajaxswing.jar:$harmony/lib/hibernate/\*:$harmony/lib/secureshare/\*:$harmony/lib/json/\*:$harmony/lib/ext/\*:$harmony/lib/uri/\* com.sodiumcow.cc.shell.Shell -h $harmony -p h -m client "$@"
