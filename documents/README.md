# Yamaha ルータを使用するに当たって


## Routing table

 「境界のルータ」について
 組織間の境界にあるルータのこと。例えば家庭や企業とISPを繋ぐルータ。

### null デバイスに送るべき IP アドレス

「境界のルータにおいて」以下のIP アドレスは、null デバイスに送るべきではないかと考える

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


