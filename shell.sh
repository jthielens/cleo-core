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

# usage:   download $artifact
# returns: the downloaded file name
download () {
    local artifact version metaversion tag tagfile url target
    artifact=$1
    version=$2
    metaversion=$(snapversion $artifact $version)
    url=$(snapurl $artifact $version $artifact-$metaversion.jar)
    tag=$(etag $url)
    target=$here/$artifact-$version.jar
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
    # download the installer
    echo "downloading $target from $url (tag=$tag file=$(cat $tagfile 2>/dev/null))" 1>&2
    wget -nv -q -O $target $url
    if [ "$tag" ]; then
        echo $tag > $tagfile
    fi
    echo $target
}

here=$(cd `dirname $0` && pwd -P)
if [ "$1" = "update" ]; then
    download cleo-labs-util      0.0.1-SNAPSHOT
    download cleo-labs-api-shell 0.0.1-SNAPSHOT
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
