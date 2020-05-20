#!/bin/sh

#-------------------------------------------------------------------------------------#
# to bootstrap the download of the cleo labs shell, type:                             #
#   wget -nv -q https://raw.githubusercontent.com/jthielens/cleo-core/master/shell.sh #
#   chmod a+x shell.sh                                                                #
#   ./shell.sh update                                                                 #
#-------------------------------------------------------------------------------------#

# usage:   bootstrap $url $path
# returns: nothing
bootstrap() {
    local url path target tag
    url=$1
    path=$2
    target=${1##*/}
    echo "downloading $path/$target from $url" 1>&2
    if wget -nv -q -S -O $path/$target $url 2>$path/$target.tmp.h; then
        tag=$(sed -n '0,/ETag/s/.*ETag: *"\(.*\)".*/\1/p' $path/$target.tmp.h)
        if [ "$tag" ]; then
            echo $tag > $path/$target.etag
        fi
        echo "successful download: $path/$target (etag=$tag)" 1>&2
        rm $path/$target.tmp.h >/dev/null 2>&1
    else
        echo "error: can't bootstrap download $url" >&2
        rm $path/$target.tmp.h >/dev/null 2>&1
        exit
    fi
}

# usage:   cleohome=$(servicehome $service)
# returns: the CLEOHOME parsed from the $service conf file
servicehome() {
    local init service
    init=`ps -p1 | grep systemd > /dev/null && echo systemd || echo upstart`
    service=$1
    if [ "$init" = "systemd" ]; then
        sed -n '/^Environment=CLEOHOME=/s/.*=\s*//p' /lib/systemd/system/$service.service 2>/dev/null
    else
        sed -n '/^env\s*CLEOHOME=/s/.*=\s*//p' /etc/init/$service.conf 2>/dev/null
    fi
}

# usage:   cleohome=$(findhome arg)
# returns: the (suspected) path to Harmony/VLTrader's install directory based on the argument
findhome() {
    local cleohome arg
    arg=$1
    cleohome=$(servicehome $arg)
    if [ "$cleohome" = "" ]; then
        if [ -e "$arg/Harmonyc" -o -e "$arg/VLTraderc"  ]; then
            cleohome=$arg
        fi
    fi
    echo $cleohome
}

here=$(cd `dirname $0` && pwd -P)
if [ "$1" = "update" ]; then
    if ! [ -e $here/cleo-util.sh ]; then
        bootstrap 'https://raw.githubusercontent.com/jthielens/versalex-ops/master/tools/cleo-util.sh' $here
    fi
    . $here/cleo-util.sh
    quiet=short
    githubassetdownload jthielens/cleo-labs-util 5.6.2 cleo-labs-util-5.6.2.0-SNAPSHOT.jar      $here >/dev/null
    githubassetdownload jthielens/cleo-core      5.6.2 cleo-labs-api-shell-5.6.2.0-SNAPSHOT.jar $here >/dev/null
    githubdownload      jthielens/versalex-ops       tools/cleo-util.sh                       $here >/dev/null
    githubdownload      jthielens/versalex-ops       service/cleo-service                     $here >/dev/null
    githubdownload      jthielens/cleo-core          shell.sh                                 $here >/dev/null
    chmod a+x $here/cleo-service
    chmod a+x $here/shell.sh
else
    cleohome=$(findhome $1)
    if [ "$cleohome" != "" ]; then
        shift;
    elif [ "$1" != "" -a -d "$1" ]; then
        # assume this was supposed to be a CLEOHOME
        :
    elif [ -e "./Harmonyc" -o -e "./VLTraderc" ]; then
        cleohome=.
    else
        cleohome=$(servicehome cleo-harmony)
    fi
    if [ "$cleohome" != "" ]; then
        cleohome=$(cd $cleohome && pwd -P)
        echo "CLEOHOME=$cleohome"
        unset DISPLAY
        classpath=$here/cleo-labs-api-shell-5.6.2.0-SNAPSHOT.jar:$here/cleo-labs-util-5.6.2.0-SNAPSHOT.jar:$(find $cleohome/lib -type d|sed 's|$|/*|'|paste -s -d : -):$cleohome/webserver/AjaxSwing/lib/ajaxswing.jar
        (cd $cleohome; ./jre/bin/java -cp $classpath com.cleo.labs.api.shell.Shell -h . -m client "$@")
    else
        echo "Cleo installation not found"
    fi
fi
