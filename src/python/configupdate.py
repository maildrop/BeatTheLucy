#!/usr/bin/python3

# 例題の中身は、https://docs.python.org/ja/3.13/library/configparser.html からとったもの
# [topsecret.server.example] の ForwardX11 の yes no を切り替えるプログラムにしている

import configparser
import fcntl
import os
import io

config = configparser.ConfigParser()

# 今 ファイルを READ/WRITE 両モードかつ 不存在の時にはファイルを作りたいので
# ランダムアクセスファイルは 標準I/O の範囲外で
# "a" フラグだと O_APPEND がついてしまう。これは lseek の動作を阻害するので、OSレイヤーのフラグを使って
# ファイルデスクリプターをオープン その後 python の fileObject へ fdopen を使って変換する

# fd: file descripor,
# mode=0o644 は パーミッション rw-rw-rw- の意味 さらに umask とのビット論理和がとられて大体 rw-r--r-- となるはず
# ここで重要なのは 実行ビットを落としておくこと。
fd = os.open( "example.ini" , os.O_RDWR | os.O_CREAT , mode=0o666)
if ( 0 < fd ):

    # ローレベルの filedescriptor を python の fileobject で開き直す
    # ランダムアクセスをするのでバッファリングをオフの バイナリモードのしておく。
    with os.fdopen( fd , "rb+" , buffering=0) as configfile: 
        fcntl.flock( fd , fcntl.LOCK_EX ) # ファイルのロックをかける 
        try:
            os.lseek( configfile.fileno() , 0 , os.SEEK_SET )
            # ファイルの中身 これをバイナリで読んでから、文字列に変換する 
            inp = configfile.read().decode() 
            print( inp ) # 現在の内容の表示
            config.read_string( inp )

            # config の中身が無いとエラーを出すので キーが無いときには作る
            if ( not ( 'topsecret.server.example' in config ) ) :
                config['topsecret.server.example'] = {}

            # 今回の主題 ここの ForwardX11 を切り替える
            topsecret = config['topsecret.server.example']
            
            if ('ForwardX11' in topsecret ) :
                topsecret['ForwardX11'] = ('no' if ( topsecret['ForwardX11'] == 'yes' ) else 'yes')
            else :
                topsecret['ForwardX11'] = ('yes' )

            # config を ファイルに書き込んだように 文字列 ss に書き込む
            with io.StringIO() as ss:
                config.write(ss)
                ss.seek(0) # rewind
                content = ss.read()
            
            # ファイルポインタを先頭に 
            os.lseek( configfile.fileno() , 0 , os.SEEK_SET )
            #  O_APPEND があるとこの動作を阻害する 
            # 正確には write のコールで os.leek( configfile.fileno() , 0 , os.SEEK_END ) が呼ばれたように
            # ファイルポインタを末尾に移動させる恐れがある。
            
            # ここで ファイルの中身を write する。
            os.write( configfile.fileno() , content.encode() )
            # 今上書きをしてきたので、残りの領域は蛇足である truncate() を呼びだして、ファイルを切り詰める
            configfile.truncate()
            # 実際にディスクに書き込まれるように fsync を使用する。
            os.fsync( configfile.fileno() )
            # 戻ってくると実際にファイルが書き込まれる
        finally:
            # ファイルが書き込まれたので、ロックを解除する（ファイルのクローズでもロックは解除される）
            fcntl.flock( fd , fcntl.LOCK_UN )  
else:
    print("cannot create or open file" )
