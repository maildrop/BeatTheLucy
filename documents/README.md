# Yamaha ルータを使用するに当たって

## ssh

現在のsshコマンドでYamahaのルータ へ ssh接続するには 鍵交換アルゴリズムが少々古いので、
OpenSSH から繋ぐ場合（Windows の ssh も含む) ~/.ssh/config へ 追加の設定をしておく

なお Key Exchange で KexAlgorithms なので、綴りに注意

```
Host 192.168.1.254
KexAlgorithms +diffie-hellman-group1-sha1
```

なお、公開鍵認証が使えるのは RTX1210 以降

## Routing table

 「境界のルータ」について
 組織間の境界にあるルータのこと。例えば家庭や企業とISPを繋ぐルータ。

### null デバイスに送るべき IP パケット

「境界のルータにおいて」以下のIPアドレス向けの、null デバイスに送るべきではないかと考える

- プライベートアドレス
    - 10.0.0.0/8
    - 172.16.0.0/12
    - 192.168.0.0/16
 
- 文書用に予約されたIPv4アドレス RFC5737
    - 192.0.2.0/24 (TEST-NET-1)
    - 198.51.100.0/24 (TEST-NET-2)
    - 203.0.113.0/24 (TEST-NET-3)

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
ip filter 200003 reject 192.168.1.0/24 * * * *
ip filter 200004 reject 192.0.2.0/24 * * * *
ip filter 200005 reject 198.51.100.0/24 * * * *
ip filter 200006 reject 203.0.113.0/24 * * * *

# 出力用
ip filter 200010 reject * 10.0.0.0/8 * * *
ip filter 200011 reject * 172.16.0.0/12 * * *
ip filter 200012 reject * 192.168.0.0/16 * * *
ip filter 200013 reject * 192.168.1.0/24 * * *
ip filter 200014 reject * 192.0.2.0/24 * * *
ip filter 200015 reject * 198.51.100.0/24 * * *
ip filter 200016 reject * 203.0.113.0/24 * * *


pp select 1
 ip pp secure filter in 200000 200001 200002 200003 200004 200005 200006  ...
 ip pp secure filter out  200010 200011 200012 200013 200014 200015 200016 ...
no pp select


```

### 注意するべきポート番号

### 1900 (UPnP: Universal Plug and Play)




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
