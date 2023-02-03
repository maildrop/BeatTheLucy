﻿# Routing table

## null デバイスに送るべき IP アドレス

以下のIP アドレスは、null デバイスに送るべきではないかと考える

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