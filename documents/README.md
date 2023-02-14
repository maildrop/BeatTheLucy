# Yamaha ルータを使用するに当たって
 
 例えば

- 少々複雑なルーティングがしたい。
    - http/https は、 帯域が欲しいので IPoE で それ以外は、 PPPoE を使いたい。
- VPN IKEv2/IPsec でネットワーク間接続をしたい
    - クラウドのVPSと接続したい。

 いろいろ理由があると思います。


# お買い物


## RTX 1200 と RTX1210 の違いについて

- RTX 1200 は、ssh の 公開鍵認証によるログインができない。
- RTX 1200 は、IPv6 RA (IPv6 RA RDNSS option)で DNS アドレスの配布が出来ない。
    - このため、DHCPv6 を別個に用意する必要がある。

## コンソールケーブル

 コンソールケーブルは無くても設定できるが、誤設定により閉め出しされる。

- RTX 1200 のコンソールは dsub 9ピン オスの端子を持ち（つまり 「dsub９ピンメス側」を持つケーブルが必要） 1210は RJ45端子 （いわゆる ciscoコンソールケーブル、あるいはその形状からきしめんケーブルと呼ばれる。） が必要。
    - コンソールケーブルは ethernet ではない。端子の形状は同じだが電気的特性が異なる。
    - ISDN は ethernet ではない。端子の形状は同じだが電気的特性が異なる。

```
 きしめんケーブルは平形のethernetケーブルを指す場合もあるが、大体 cisco の コンソールケーブルのこと。
```

# 設定

## ssh

現在のsshコマンドでYamahaのルータ へ ssh接続するには 鍵交換アルゴリズムが少々古いので、
OpenSSH から繋ぐ場合（Windows の ssh も含む) ~/.ssh/config へ 追加の設定をしておく

なお Key Exchange で KexAlgorithms なので、綴りに注意

```
Host 192.168.1.254
KexAlgorithms +diffie-hellman-group1-sha1
```

なお、公開鍵認証が使えるのは RTX1210 以降であり、お買い物するときには気をつけて。

## Routing table

 「境界のルータ」について
 組織間の境界にあるルータのこと。例えば家庭や企業とISPを繋ぐルータの事を指す。

### null デバイスに送るべき IP パケット

「境界のルータにおいて」以下のIPアドレス向けの、null に送るべきではないかと考える

```
null とは、破棄するパケットを送りつける先、ここに送られたパケットは正常に破棄される。破棄するためのインターフェース。
```

- プライベートアドレス
    - 10.0.0.0/8
    - 172.16.0.0/12
    - 192.168.0.0/16
 
- 文書用に予約されたIPv4アドレス RFC5737
    - 192.0.2.0/24 (TEST-NET-1)
    - 198.51.100.0/24 (TEST-NET-2)
    - 203.0.113.0/24 (TEST-NET-3)

文書用に予約されたIPv4アドレスを追加しておくのは、

- インターネット上では使われないことがあらかじめ分かっている IP アドレスであること。
- 文書上で使用されるために、コピペしたときに誤って default gateway から外へ出て行くことを防ぐため

等の理由であるため。

以上を Yamaha RTX のコマンドにすると
```
ip route 10.0.0.0/8 gateway null
ip route 172.16.0.0/12 gateway null
ip route 192.168.0.0/16 gateway null
ip route 192.0.2.0/24 gateway null
ip route 198.51.100.0/24 gateway null
ip route 203.0.113.0/24 gateway null
```

である。

## ip filter

「境界のルータ」が外側から受けとるIPパケットの内 上の「プライベートアドレス」 「予約されたIPv4アドレス」をソースとしたパケットは破棄するよう
に設定したほうがよいと考える。

```
# 入力用
ip filter 200000 reject 10.0.0.0/8 * * * *
ip filter 200001 reject 172.16.0.0/12 * * * *
ip filter 200002 reject 192.168.0.0/16 * * * *
ip filter 200004 reject 192.0.2.0/24 * * * *
ip filter 200005 reject 198.51.100.0/24 * * * *
ip filter 200006 reject 203.0.113.0/24 * * * *

# 出力用
ip filter 200010 reject * 10.0.0.0/8 * * *
ip filter 200011 reject * 172.16.0.0/12 * * *
ip filter 200012 reject * 192.168.0.0/16 * * *
ip filter 200014 reject * 192.0.2.0/24 * * *
ip filter 200015 reject * 198.51.100.0/24 * * *
ip filter 200016 reject * 203.0.113.0/24 * * *


pp select 1
 ip pp secure filter in 200000 200001 200002 200004 200005 200006  ...
 ip pp secure filter out  200010 200011 200012 200014 200015 200016 ...
no pp select
```

### 注意するべきポート番号

 SMB (Windows ファイル共有の事) のポートはフィルタで閉じているが、{L2TP,IKEv2}/IPsec などリリモートで使う時に、どこで閉じているか注意。


### 1900 (UPnP: Universal Plug and Play)

 最低でもWAN側（例えば pp) の UPnP は塞ぐ。そして IPv6 も塞ぐ。正しく使うのは難しいので機能自体を無効にするのも手。

# 事例

## コンソールケーブルのオスメス間違い

 Dsub の オスメスは、注意。今必要なのはオスなの？メスなの？

## lan3 のインターフェースが上がっても implicit ルーティングが追加されない

[ip lan3 address](http://www.rtpro.yamaha.co.jp/RT/manual/rt-common/ip/ip_interface_address.html)
を指定した場合 ルーティングテーブルに implicit ルーティングが追加されるはずである。

- 原因 
    - 既に同一ネットワーク static ルーティングが追加されている場合には implicit ルーティングは追加されない。

## MTU（Maximum Transmission Unit) の設定
 Ethernet の MTU は 1500 octet (1octet は 8bit で byte と同じだけどbyte は IBM用語なので、通信では、octet が単位）
 しかしながら、PPPoE を使う場合L2TP接続が行われ MTU は 1500 octet より小さくなる。
- Flet's の場合は、
    - PPPoE MTU は 1454 octet
    - IPoE IPv4 over IPv6
        - DS-Lite は 1460 octet
- au光 しらんなぁ。

### MTU の設定をしないとどうなるか？
- パケットのフラグメンテーション処理が ルータ上で行われる
- ルータのCPU使用率があがる。

 fast.com の計測でも 大体CPU使用率の最大値が 90% ぐらい 平均値は 0% に張り付くぐらい。
 つまり 殆どCPUは使われてない。はず。それ以上に使われている時には、何か問題がある -> MTU の設定を確認し直す。

### なぜ設定する必要が？
 そもそも MTU は接続先/接続方法によって変わる。
 特に、tunnelインターフェースはppインターフェースのMTUからヘッダサイズを計算して自分で算出しなおす必要がある。


## VPN?
### IKEv2/IPsec 
 IPhone つながります。

# IPoE

## NAT のセッション数
 IPoE ( IPv4 over IPv6 , DS-Lite ) を使用する際には、NATのセッション数の節約が必要になる場合がある。

 UDPはセッションの終わりの検出する術が無いので、NATはタイムアウトするまでセッションを保持する必要がある。（通常 ５分とか１０分とか）ために、
このNATをセッション数を浪費する原因の一つに、DNS の UDPパケットがある。
 
 DNS の forward は、IPv6 アドレスを使用すること。 間違っても 8.8.8.8 や、1.1.1.1 を使ってはならない。
 もし使うのならば Google の Public DNS IP v6 アドレスは
 
- 2001:4860:4860::8888
- 2001:4860:4860::8844

 である。

 また、Chrome cast のような機器は、製品の死活活性のためか頑なに 8.8.8.8 を使用しようとするので、対応を考える

- 8.8.8.8 へのパケットをlan入力時にフィルタする。

