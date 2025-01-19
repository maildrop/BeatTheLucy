#!/usr/bin/python3
# -*- coding: utf-8 -*-

#
# 例題の中身は、https://docs.python.org/ja/3.13/library/configparser.html からとったもの
# [topsecret.server.example] の ForwardX11 の yes no を切り替えるプログラムにしている
# このプログラムの目標は、 inotify 等 ディレクトリ監視用のAPI を使うプログラムと連携し example.ini が更新された事を
# 検出するために使う（そのために同一のファイルを open - lock - write - flush - unlock - close する)
# 例えば inotifywait -m example.ini として example.ini を監視しながらこのプログラムを実行すると
#
# mit@eva:python $ inotifywait -m example.ini
# Setting up watches.
# Watches established.
# example.ini OPEN
# example.ini ACCESS
# example.ini MODIFY
# example.ini MODIFY
# example.ini CLOSE_WRITE,CLOSE
#
# というように 書き込みが行われる度に、 CLOSE_WRITE が出力される。
#

import configparser
import fcntl
import os
import io

config = configparser.ConfigParser()

# 今 ファイルを READ/WRITE 両モードかつ 不存在の時にはファイルを作りたいので
# ランダムアクセスファイルは 標準I/O の範囲外で
# "a" フラグだと O_APPEND がついてしまう。これは lseek の動作を阻害するので、OSレイヤーのフラグを使って
# ファイルデスクリプターをオープン その後 python の fileObject へ fdopen を使って変換する

# ファイルのオープン low level の open(2) システムコールでファイルを開く
# fd: file descripor,
# mode=0o666 は パーミッション rw-rw-rw- の意味 さらに umask とのビット論理和がとられて大体 rw-r--r-- となるはず
# umask については man umask で確認すること。
# ここで重要なのは 実行ビットを落としておくこと。 

# os.O_RDWR 読み書き可能でファイルをオープンする
# os.O_CREAT ファイルが無い場合にはファイルを作成する

# なぜ os.open() を使うのか？
# "w" だと open したときに truncate される
# "a" だと、後述の O_APPEND がつく
# "r+" だと、ファイルが存在しないときに失敗する
# ということがあり、存在しない場合には、大きさ0で開く 存在する場合はそのまま開く 動作ができないため。

fd = os.open( "example.ini" , os.O_RDWR | os.O_CREAT , mode=0o666)
if ( not( fd < 0 ) ): # fd は 0以上 であれば成功 (0は大体標準入力につながっている) 失敗は-1 errno に理由
    # ローレベルの filedescriptor を python の fileobject で開き直す
    # ランダムアクセスをするのでバッファリングをオフの バイナリモードのしておく。
    with os.fdopen( fd , "rb+" , buffering=0) as configfile: 
        fcntl.flock( fd , fcntl.LOCK_EX ) # ファイルのロックをかける 
        try:
            # ファイルポインタを ファイルの先頭に移動させる
            os.lseek( configfile.fileno() , 0 , os.SEEK_SET )
            # ファイルの中身 バイナリモードなのでread() の戻り値は、blob それを decode() で文字列に変換する 
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
