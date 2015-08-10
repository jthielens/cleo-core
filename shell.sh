#!/bin/sh

# where is Nexus
nexus=10.80.80.156

# usage:   etag $url
# returns: the ETag for $url, or error if the connection fails
etag () {
    local url
    url=$1
    if headers=`wget -S --spider -nv $url 2>&1`; then
        echo $headers | sed -n 's/.*ETag: *"\(.*\)".*/\1/p'
    else
        echo error
    fi
}

# usage:   snapurl $artifact $version $file
# returns: the Cleo snapshots repository URL for $artifact and $file
#          hard-coded for group=com.cleo.labs
snapurl () {
    local artifact version file
    artifact=$1
    version=$2
    file=$3
    echo http://$nexus/nexus/content/repositories/snapshots/com/cleo/labs/$artifact/$version/$file
}

# usage:   snapversion $artifact
# returns: the correct snapshot version of $artifact
snapversion() {
    local artifact version
    artifact=$1
    version=$2
    wget -nv -q -O- $(snapurl $artifact $version maven-metadata.xml) | sed -n '0,/<value>/s/.*<value>\(.*\)<\/value>/\1/p'
}

# usage:   download $url $target
# returns: the downloaded file name
download() {
    local url target tag tagfile
    url=$1
    target=$2
    tag=$(etag $url)
    tagfile=$target.etag
    if [ "$tag" = "error" ]; then
        echo "connection error: reusing cached $target" 1>&2
        echo $target; return
    elif [ -f $tagfile -a "$tag" ]; then
        if [ "$tag" = $(cat $tagfile 2>/dev/null) ] ; then
            echo "cache etag matches: reusing cached $target" 1>&2
            echo $target
            return
        fi
    fi
    # download the target
    echo "downloading $target from $url (tag=$tag file=$(cat $tagfile 2>/dev/null))" 1>&2
    wget -nv -q -O $target $url
    if [ "$tag" ]; then
        echo $tag > $tagfile
    fi
    echo $target
}

# usage:   downloadjar $artifact $version
# returns: the downloaded file name
downloadjar () {
    local artifact version metaversion url target
    artifact=$1
    version=$2
    metaversion=$(snapversion $artifact $version)
    url=$(snapurl $artifact $version $artifact-$metaversion.jar)
    target=$here/$artifact-$version.jar
    echo $(download $url $target)
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
    downloadjar cleo-labs-util      0.0.1-SNAPSHOT > /dev/null
    downloadjar cleo-labs-api-shell 0.0.1-SNAPSHOT > /dev/null
    download    'https://raw.githubusercontent.com/jthielens/cleo-core/master/shell.sh' $0 > /dev/null
else
    cleohome=$(findhome $1)
    if [ "$cleohome" != "" ]; then
        shift;
    elif [ "$1" != "" -a -d $1 ]; then
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
        classpath=$here/cleo-labs-api-shell-0.0.1-SNAPSHOT.jar:$here/cleo-labs-util-0.0.1-SNAPSHOT.jar:$cleohome/lib/\*:$cleohome/lib/help/\*:$cleohome/webserver/AjaxSwing/lib/ajaxswing.jar:$cleohome/lib/hibernate/\*:$cleohome/lib/secureshare/\*:$cleohome/lib/json/\*:$cleohome/lib/jersey/\*:$cleohome/lib/ext/\*:$cleohome/lib/uri/\*
        (cd $cleohome; ./jre/bin/java -cp $classpath com.cleo.labs.api.shell.Shell -h . -m client "$@")
    else
        echo "Cleo installation not found"
    fi
fi
