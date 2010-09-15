YalerTunnel - generic tunneling via the Yaler relay infrastructure

Copyright (c) 2010, Oberon microsystems AG, Switzerland. All rights reserved.

YalerTunnel makes any service (e.g. a Web server or a SSH daemon) accessible
from the Internet, no matter if the service is behind a firewall, a NAT, or a
mobile network gateway.

If the service is based on HTTP, YalerTunnel is started in server mode,
otherwise in proxy mode. The client either knows HTTP (e.g. a Web browser),
supports HTTP proxies (e.g. PuTTY), or uses a second instance of YalerTunnel
started in client mode to access the tunneled service.


To build and run the program, first make sure that you have JDK6 installed and
that your PATH environment variable includes the JDK's bin directory. Then type:

    javac YalerTunnel.java


The following examples assume that the Yaler relay infrastructure is running on
http://yaler.net/


Example 1) Running a Web server behind a firewall

Start a Web server listening on port 80 and YalerTunnel in server mode:

    java YalerTunnel server localhost:80 yaler.net:80 my-device

On the client, open a Web browser and access

    http://yaler.net/my-device


Example 2) Tunneling SSH

Start a SSH daemon listening on port 22 and YalerTunnel in proxy mode:

    java YalerTunnel proxy localhost:22 yaler.net:80 my-device-ssh

If your SSH client supports HTTP proxies, configure it to connect via

    http://yaler.net/my-device-ssh

Otherwise, start YalerTunnel in client mode

    java YalerTunnel client localhost:10022 yaler.net:80 my-device-ssh

and connect the SSH client to localhost:10022.


LICENSE: Yaler and YalerTunnel are released under the Sleepycat license with the
additional clause "FOR NON-COMMERCIAL PURPOSES".

Thanks, and please join us at http://yaler.org/

Cuno (pfister@oberon.ch), Marc (frei@oberon.ch), Thomas (amberg@oberon.ch)