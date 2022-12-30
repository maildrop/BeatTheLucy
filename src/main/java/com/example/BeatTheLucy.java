package com.example;

import java.lang.*;
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

    /**
      実行時エントリーポイント
     */
    public static final void main( final String args[] ){
        System.out.println( "hello world" );
        beatTheLucy(7777);
        return;
    }

    /**
       Selector の selectedKeys() が各々行う処理
     */
    private interface Operation{
        public void operate( SelectionKey selectionKey );
    }
    
    public static final void beatTheLucy(final int port){

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

    // クライアントからの読み取り実行
    private static final class ReaderOp implements Operation{
        private java.nio.ByteBuffer buffer;
        private java.nio.charset.CharsetDecoder decoder;
        private java.nio.CharBuffer cb;
        private ReaderOp(){
            this.buffer = java.nio.ByteBuffer.allocate( 4096 ); // 4k page 
            this.decoder = java.nio.charset.StandardCharsets.UTF_8.newDecoder();
            this.cb = java.nio.CharBuffer.allocate( 4096 ); // 4k page
        }

        public void operate( final SelectionKey selectionKey ){
            logger.entering( "ReaderOp" , "operate" );
            try{
                assert selectionKey.isReadable() ;
                final java.nio.channels.SocketChannel socket = java.nio.channels.SocketChannel.class.cast( selectionKey.channel() );

                for(;;){
                    assert 0 < buffer.remaining();
                    final int read_size = socket.read( buffer );
                    if( read_size == 0 ){
                        break;
                    }else if( read_size == -1 ){
                        buffer.flip();
                        logger.info( "disconnect by " + String.valueOf( socket ) );
                        selectionKey.cancel();
                        socket.close();
                        break;
                    }else{
                        assert 0 < read_size ;
                        buffer.flip();
                        final var result = decoder.decode( buffer , cb , false );
                        if( result == UNDERFLOW ){
                            { // デコード済みのCharBufferを処理する
                                final int pos = cb.position();
                                if( cb.hasArray() ){
                                    
                                }else{
                                    
                                }
                                cb.rewind();
                                char c[] = new char[pos];
                                cb.get( c );
                                logger.info( "underflow" + " " + new String( c ) );
                            }
                            
                            if( 0 < buffer.remaining() ){
                                final byte remain[] = new byte[buffer.remaining()];
                                buffer.get(remain);
                                buffer.rewind();
                                buffer.put(remain);
                            }else{
                                buffer.rewind();
                            }
                            continue;
                        }else if( result == OVERFLOW ){
                            logger.warning( "OVERFLOW" + String.valueOf( cb.remaining() ));
                            
                        }else{
                            logger.warning( "what?");
                        }
                    }
                }
                
            }catch( final IOException ioe ){
                logger.warning( String.valueOf( ioe ));
            }
            logger.exiting( "ReaderOp" , "operate" );
        }
    }

    // クライアントからのconnectionを受け取る
    private static final class Acceptor implements Operation{
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
    }
}
