package com.example;
import java.lang.*;
import java.util.Observable;
import java.util.logging.Logger;
import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import static java.nio.charset.CoderResult.UNDERFLOW;
import static java.nio.charset.CoderResult.OVERFLOW;

/**
   Lucy に打ち勝て！
   // com.example.BeatTheLucy
   echo "我が輩は猫である" | nc -q 0 ipv4addr 7777
*/
public final class BeatTheLucy{
    private static final Logger logger = Logger.getLogger(BeatTheLucy.class.getName());

    private static class QueueEntry{
        public SelectionKey selectionKey;
        public String message;
    }

    private void queueMessage( final QueueEntry queueEntry ){
        synchronized( queueEntry ){
            logger.info( String.valueOf( queueEntry.selectionKey ) + " " + String.valueOf( queueEntry.message ) );
        }

        final var selector = queueEntry.selectionKey.selector();
        final var keyset =selector.keys();
        synchronized( keyset ){
            for( final var k : keyset ){
                final Object obj = k.attachment();
                if( ReaderOp.class.isInstance(obj) ){
                    final ReaderOp target = ReaderOp.class.cast( obj );
                    target.message( k, queueEntry.message );
                }
            }
        }
    }
    
    /**
       Selector の selectedKeys() が各々行う処理
     */
    private static interface Operation{
        public void operate( SelectionKey selectionKey );
    }

    /**
       クライアントからの要求実行
    */
    private final class ReaderOp implements Operation{
        private final java.nio.ByteBuffer read_buffer;
        private final java.nio.charset.CharsetDecoder decoder;
        private final java.nio.CharBuffer cb;
        private final java.nio.ByteBuffer write_buffer;

        private ReaderOp(){
            this.read_buffer = java.nio.ByteBuffer.allocate( 8 /* 4096 */ ); // 4k page 
            this.decoder = java.nio.charset.StandardCharsets.UTF_8.newDecoder();
            this.cb = java.nio.CharBuffer.allocate( 4096 / 2  );     // 4k page
            this.write_buffer = java.nio.ByteBuffer.allocate( 4096 );// 4k page
        }

        public final void message( final SelectionKey selectionKey , final String message ){
            logger.info( String.valueOf( selectionKey ) + ":" + String.valueOf( message ) );
            synchronized( write_buffer ){
                write_buffer.put( (message+"\n").getBytes( java.nio.charset.StandardCharsets.UTF_8 ) );
            }
            selectionKey.interestOps( selectionKey.interestOps() | SelectionKey.OP_WRITE );
        }
        
        /**
           読み取り後の入力の処理
           ここでは、ByteBufferの入力を String へ変換する
         */
        private final void input_processing(final SelectionKey selectionKey, final boolean endOfInput ){
            synchronized( decoder ){
                synchronized( cb ){
                    final var result = decoder.decode( read_buffer , cb , endOfInput );
                    assert (result == UNDERFLOW || result == OVERFLOW ): "CorderResultの想定していない戻り値" ;
                    if( result == UNDERFLOW || result == OVERFLOW ){
                        // 入力側のByteBufferで 未処理の部分をByteBufferに詰め直す
                        if( 0 < read_buffer.remaining() ){
                            final byte remain[] = new byte[read_buffer.remaining()];
                            read_buffer.get(remain);
                            read_buffer.rewind();
                            read_buffer.put(remain);
                        }else{
                            read_buffer.rewind();
                        }
                    }
                    
                    // デコーダの出力の処理
                    if( result == UNDERFLOW ){
                        { // デコード済みのCharBufferを処理する
                            final char c[] = new char[cb.position()];
                            cb.rewind();
                            cb.get( c );

                            // 改行単位で分割する
                            {
                                int begin = 0;
                                int pos = 0;
                                for( ; pos < c.length ; ++pos ){
                                    if( c[pos] == '\n' ){
                                        final QueueEntry entry = new QueueEntry();
                                        synchronized( entry ){
                                            entry.selectionKey = selectionKey;
                                            entry.message = new String( c, begin , pos-begin );
                                        }
                                        queueMessage( entry );
                                        begin = pos+1;
                                    }
                                }
                                assert begin <= c.length;
                                if( begin < c.length ){
                                    cb.rewind();
                                    cb.put( c , begin , c.length - begin );
                                }else if( begin == c.length ){
                                    cb.clear();
                                }
                                
                                if( endOfInput ){ // 入力終端の場合は残りがあれば全部を String へ変換する
                                    if( begin < pos ){
                                        final QueueEntry entry = new QueueEntry();
                                        entry.message = new String( c, begin , pos-begin );
                                        queueMessage( entry );
                                    }else if( begin == pos ){

                                    }
                                }
                            }
                        }
                    }else if( result == OVERFLOW ){
                        logger.warning( "OVERFLOW" + String.valueOf( cb.remaining() ));
                    }else{
                        logger.warning( "what?");
                    }
                }
            }
        }
        
        /**
           読み取りの実装
           @return Channel の終端に到達した場合は -1 が返る それ以外は 0以上の値が返る
         */
        private final int do_read( final SelectionKey selectionKey )
            throws IOException{
            final java.nio.channels.SocketChannel socket = java.nio.channels.SocketChannel.class.cast( selectionKey.channel() );
            synchronized( read_buffer ){
                synchronized( socket ){
                    for(;;){
                        assert 0 < read_buffer.remaining();

                        final int read_size = socket.read( read_buffer );
                        if( read_size == 0 ){
                            return 0;
                        }else if( read_size == -1 ){
                            read_buffer.flip();
                            this.input_processing( selectionKey, true );
                            logger.info( "disconnect by " + String.valueOf( socket ) );
                            return -1;
                        }
                        assert 0 < read_size ;
                        read_buffer.flip();
                        this.input_processing( selectionKey, false );
                        continue;
                    }
                }
            }
        }

        /**
           書き込みの実装
           @return 特に意味が無い
         */
        private final int do_write( final SelectionKey selectionKey )
            throws IOException{
            final java.nio.channels.SocketChannel socket = java.nio.channels.SocketChannel.class.cast( selectionKey.channel() );
            synchronized( write_buffer ){ // TODO 書き込みの処理が不十分
                write_buffer.flip();
                socket.write( write_buffer );
                write_buffer.clear();
            }
            // TODO いま一時的に書き込みをここで無効にしている
            selectionKey.interestOps( selectionKey.interestOps() & ( ~SelectionKey.OP_WRITE ) );
            return 0;
        }
        
        public void operate( final SelectionKey selectionKey ){
            logger.entering( "ReaderOp" , "operate" );
            try{
                final int interestOps = selectionKey.interestOps();
                if( ((interestOps & SelectionKey.OP_READ) != 0 ) && selectionKey.isReadable() ){
                    // 読み取りに興味があり、実際に読み取りが可能ならば
                    final int val = this.do_read( selectionKey ); // 読み取り実行
                    if( val == -1 ){ 
                        // 読み取りが終端に達しているので、 selectionKey の interestOps で OP_READ フラグを落とす
                        selectionKey.interestOps( selectionKey.interestOps() & ( ~SelectionKey.OP_READ ) );
                    }
                }else if( ((interestOps & SelectionKey.OP_WRITE) != 0 )&& selectionKey.isWritable() ){
                    // 書き込みに興味があり、実際に書き込みが可能ならば
                    this.do_write( selectionKey );
                }

                // selectionKey.interestOps() が OP_READ OP_WRITE のフラグが共に落ちている時にはもう閉じる
                if( (selectionKey.interestOps() & ( SelectionKey.OP_READ | SelectionKey.OP_WRITE ) ) == 0 ){
                    final java.nio.channels.SocketChannel socket = java.nio.channels.SocketChannel.class.cast( selectionKey.channel() );
                    selectionKey.cancel();
                    socket.close();
                }

            }catch( final IOException ioe ){
                logger.warning( String.valueOf( ioe ));
                try{
                    final java.nio.channels.SocketChannel socket = java.nio.channels.SocketChannel.class.cast( selectionKey.channel() );
                    socket.close();
                }catch( final IOException closeing_exception ){
                    logger.severe( String.valueOf( closeing_exception ));
                }
            }
            logger.exiting( "ReaderOp" , "operate" );
            return;
        }
    } // end of ReaderOp 

    // クライアントからのconnectionを受け取る
    private final class Acceptor implements Operation{
        public void operate( final SelectionKey selectionKey ){
            try{
                final java.nio.channels.ServerSocketChannel sc = java.nio.channels.ServerSocketChannel.class.cast( selectionKey.channel() );
                final java.nio.channels.SocketChannel channel = sc.accept();
                channel.configureBlocking( false );
                logger.info( channel.toString() );
                channel.register( selectionKey.selector() , SelectionKey.OP_READ , new ReaderOp() );
            }catch( final IOException ioe ){
                logger.warning( String.valueOf( ioe ));
            }
        }
    } // end of Acceptor
    
    public final void beatTheLucy(final int port){

        assert 0 < port ;
        assert port < 65536;
        
        try( final Selector selector = Selector.open() ){
            logger.info( String.format( "openSelector" ) );

            END_OF_SERVICE:
            do{
                try( final java.nio.channels.ServerSocketChannel serverSocketChannel =
                     java.nio.channels.ServerSocketChannel.open() ){
                    try{
                        serverSocketChannel
                            .bind( new java.net.InetSocketAddress( port ) )
                            .configureBlocking( false );
                        final var key = serverSocketChannel.register( selector , SelectionKey.OP_ACCEPT , new Acceptor() );

                        try{
                            while( !selector.keys().isEmpty() ){  // Selectorに登録されたチャンネルがあるあいだ。
                                if( 0 < selector.select() ){
                                    logger.info( "select()" );
                                    final java.util.Iterator<SelectionKey> ite = selector.selectedKeys().iterator();
                                    while( ite.hasNext() ){
                                        final SelectionKey selectionKey = ite.next();
                                        ite.remove();
                                        
                                        Operation operation = Operation.class.cast( selectionKey.attachment() );
                                        if( operation != null){
                                            try{
                                                operation.operate( selectionKey );
                                            }catch( final Exception except ){
                                                logger.severe( String.valueOf( except ));
                                            }
                                        }
                                    }
                                }else{
                                    logger.info( "selector.select() == 0" );
                                }
                            }
                        }finally{
                            key.cancel();
                        }
                    }catch( final IOException ioe ){
                        break END_OF_SERVICE;
                    }
                }
            }while( false );
            
        }catch( final IOException ioe ){
            logger.warning( String.valueOf( ioe ));
        }
    }

    /**
      実行時エントリーポイント
     */
    public static final void main( final String args[] ){
        System.out.println( "hello world" );
        new BeatTheLucy().beatTheLucy(7777);
        return;
    }
}
