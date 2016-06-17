![screenshots](https://github.com/atomarea/FlowX/raw/Main/Screenshots/readme.png)

[![Google Play](http://cloud.atom-area.net/pics/GooglePlay.png)](https://play.google.com/store/apps/details?id=net.atomarea.flowx)

# FlowX

FlowX is a fork of Conversation (https://github.com/siacs/Conversations)
We startet to changed a lot of UI Stuff and setup some useful features like integrated Audio Messages and so on.

## Design principles

* Be as beautiful and easy to use as possible without sacrificing security or
  privacy
* Material Design like other cool clients like Whatsapp, Telegram etc.
* Rely on existing, well established protocols (XMPP)
* Do not require a Google Account or specifically Google Cloud Messaging (GCM)
* Require as few permissions as possible

## Features

* End-to-end encryption with [OMEMO]
* Emoji Support (Button)
* Status Messages in contact details
* Share/send your location,Audio Messages, Files, Images and so on.
* Indication when your contact has read your message
* Intuitive UI that follows Android Design guidelines
* Pictures / Avatars for your Contacts
* Syncs with desktop client (Carbon)
* Conferences (MUC)
* Very low impact on battery life

### XMPP Features

FlowX works with every XMPP server out there. However XMPP is an
extensible protocol. These extensions are standardized as well in so called
XEP's. FlowX supports a couple of these to make the overall user
experience better. There is a chance that your current XMPP server does not
support these extensions; therefore to get the most out of FlowX you
should consider either switching to an XMPP server that does or — even better —
run your own XMPP server for you and your friends. These XEP's are:

* [XEP-0065: SOCKS5 Bytestreams](http://xmpp.org/extensions/xep-0065.html) (or mod_proxy65). Will be used to transfer
  files if both parties are behind a firewall (NAT).
* [XEP-0163: Personal Eventing Protocol](http://xmpp.org/extensions/xep-0163.html) for avatars and OMEMO.
* [XEP-0191: Blocking command](http://xmpp.org/extensions/xep-0191.html) lets you blacklist spammers or block contacts
  without removing them from your roster.
* [XEP-0198: Stream Management](http://xmpp.org/extensions/xep-0198.html) allows XMPP to survive small network outages and
  changes of the underlying TCP connection.
* [XEP-0280: Message Carbons](http://xmpp.org/extensions/xep-0280.html) which automatically syncs the messages you send to
  your desktop client and thus allows you to switch seamlessly from your mobile
  client to your desktop client and back within one FlowX.
* [XEP-0237: Roster Versioning](http://xmpp.org/extensions/xep-0237.html) mainly to save bandwidth on poor mobile connections
* [XEP-0313: Message Archive Management](http://xmpp.org/extensions/xep-0313.html) synchronize message history with the
  server. Catch up with messages that were sent while FlowX was
  offline.
* [XEP-0352: Client State Indication](http://xmpp.org/extensions/xep-0352.html) lets the server know whether or not
  FlowX is in the background. Allows the server to save bandwidth by
  withholding unimportant packages.
* [XEP-0363: HTTP File Upload](http://xmpp.org/extensions/xep-0363.html) allows you to share files in conferences
  and with offline contacts.

## Team

#### Head of Development

* [Dominic Schubert](https://github.com/DoM1niC)
* [Tom Janke](https://github.com/Tom7353)
* [Christian Schneppe](https://github.com/kriztan)
* [Dominik Wagner](https://github.com/sar3th)
* [Daniel Gultsch](https://github.com/inputmice)

#### Code Contributions

(In order of appearance)

* [Rene Treffer](https://github.com/rtreffer) ([PRs](https://github.com/siacs/Conversations/pulls?utf8=%E2%9C%93&q=is%3Apr+author%3Artreffer+is%3Amerged))
* [Andreas Straub](https://github.com/strb) ([PRs](https://github.com/siacs/Conversations/pulls?utf8=%E2%9C%93&q=is%3Apr+author%3Astrb+is%3Amerged))
* [Alethea Butler](https://github.com/alethea) ([PRs](https://github.com/siacs/Conversations/pulls?utf8=%E2%9C%93&q=is%3Apr+author%3Aalethea+is%3Amerged))
* [M. Dietrich](https://github.com/emdete) ([PRs](https://github.com/siacs/Conversations/pulls?utf8=%E2%9C%93&q=is%3Apr+author%3Aemdete+is%3Amerged))
* [betheg](https://github.com/betheg) ([PRs](https://github.com/siacs/Conversations/pulls?utf8=%E2%9C%93&q=is%3Apr+author%3Abetheg+is%3Amerged))
* [Sam Whited](https://github.com/SamWhited) ([PRs](https://github.com/siacs/Conversations/pulls?utf8=%E2%9C%93&q=is%3Apr+author%3ASamWhited+is%3Amerged))
* [BrianBlade](https://github.com/BrianBlade) ([PRs](https://github.com/siacs/Conversations/pulls?utf8=%E2%9C%93&q=is%3Apr+author%3ABrianBlade+is%3Amerged))

#### How do I backup / move FlowX to a new device?
In settings under advances settings there is a option to backuo your current chat history. If you reinstall or delete FlowX, you could restore/import this history from your storage.  

### Security
* OMEMO works even when a contact is offline, and works with multiple devices. It also allows asynchronous file-transfer when the server has [HTTP File Upload](http://xmpp.org/extensions/xep-0363.html). However, OMEMO is not as widely supported as OTR and is currently implemented only by Conversation and Gajim. OMEMO should be preferred over OTR for contacts who use FlowX.

#### I get 'Incompatible Server'

As regular user you should be picking a different server. The server you selected
is probably insecure and/or very old.

If you are a server administrator you should make sure that your server provides
STARTTLS. XMPP over TLS (on a different port) is not sufficient.

On rare occasions this error message might also be caused by a server not providing
a login (SASL) mechanism that FlowX is able to handle. FlowX supports
SCRAM-SHA1, PLAIN, EXTERNAL (client certs) and DIGEST-MD5.

#### I found a bug

Please report it to our [issue tracker][issues]. If your app crashes please
provide a stack trace. If you are experiencing misbehavior please provide
detailed steps to reproduce. Always mention whether you are running the latest
Play Store version or the current HEAD. If you are having problems connecting to
your XMPP server your file transfer doesn’t work as expected please always
include a logcat debug output with your issue (see above).

[issues]: https://github.com/atomarea/FlowX/issues
