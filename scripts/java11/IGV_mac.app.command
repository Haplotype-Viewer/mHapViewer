#!/bin/sh

#This script is intended for launching on Macs
#It may or may not work on *nix, definitely not on windows

#apple.laf.useScreenMenuBar for Macs, to put menu bar at top of screen
#-Xdock:name again for Macs, sets the name in menu bar
#-Xmx4g indicates 4 gb of memory, adjust number up or down as needed
#Add the flag -Ddevelopment = true to use features still in development
prefix=`dirname $(readlink $0 || echo $0)`

# Check whether or not to use the bundled JDK
if [ -d "${prefix}/../jdk-11" ]; then
    echo echo "Using bundled JDK."
    JAVA_HOME="${prefix}/../jdk-11"
    PATH=$JAVA_HOME/bin:$PATH
else
    echo "Bundled JDK not found.  Using system JDK."
fi

exec java --module-path="${prefix}/../Java/lib" -Xmx4g \
    @"${prefix}/../Java/igv.args" \
    -Xdock:name="IGV" \
    -Xdock:icon="${prefix}/../Resources/IGV_64.png" \
    -Dapple.laf.useScreenMenuBar=true \
    -Djava.net.preferIPv4Stack=true \
    --module=org.igv/org.broad.igv.ui.Main
