package com.example;
import java.lang.*;
import java.nio.channels.SelectionKey;
import org.apache.commons.cli.Options;

public final class BeatTheLucySeven implements Runnable{
    private static final java.util.logging.Logger logger =
        java.util.logging.Logger.getLogger( BeatTheLucySeven.class.getName() );

    private static final class RuntimeConfig{
        public boolean requestShutdown;
        public java.nio.channels.Pipe.SourceChannel sourceChannel;
        public RuntimeConfig(){
            this.requestShutdown = false;
            this.sourceChannel = null;
        }
    }
    
    private static final void entry_point( final RuntimeConfig config ){
        try{
            /*
              シャットダウン処理のための仕組み
              ここで作成するPipe.SinkChannel を閉じると、対向の souceChannel#read() が -1 を返す。
              この仕組みを利用してSelectorのスレッドを終了させる。
             */
            
            /* shutdown 処理用の PipeChannel */
            final java.nio.channels.Pipe pipe = java.nio.channels.Pipe.open();
            final java.nio.channels.Pipe.SinkChannel sinkChannel = pipe.sink();
            synchronized( config ){
                config.sourceChannel = pipe.source();
                config.sourceChannel.configureBlocking( false );
            }
            final Thread thread = new Thread( new BeatTheLucySeven( config ) );

            /*
              このshutdownHook の型は、 Thread を継承した「何かの型」になる。
              hookProgress を後で利用するために、varで受けて「何かの型」を保持する
             */
            {
                final var shutdownHook = new Thread(){
                        public boolean hookProgress = false;
                        public synchronized void run(){
                            hookProgress = true;
                            // shutdown Hook 内では、既にLoggerが閉じている場合がある。
                            try{
                                sinkChannel.close();
                                try{
                                    thread.join();
                                }catch( final java.lang.InterruptedException ie ){
                                    System.err.println(String.valueOf( ie ) );
                                }
                            }catch( final java.io.IOException ioe ){
                                System.err.println( String.valueOf( ioe ));
                            }
                        }
                    };
                
                Runtime.getRuntime().addShutdownHook( shutdownHook ); // 戻り値は void 
                
                thread.start();
                try{
                    thread.join();
                }catch( final InterruptedException ie ){
                    logger.severe( String.valueOf( ie ) );
                }finally{
                    synchronized( shutdownHook ){
                        /*
                          上の shutdownHook を var で受けているので hookProgressが見える
                        */
                        if(! shutdownHook.hookProgress ){
                            try{
                                if( ! Runtime.getRuntime().removeShutdownHook( shutdownHook ) ){
                                    logger.warning( "removeShutdownHook() failed" );
                                }
                            }catch( final java.lang.IllegalStateException ise ){
                                logger.severe( String.valueOf( ise ));
                            }
                            sinkChannel.close();
                        }
                    }
                }
            }
        }catch( final java.io.IOException ioe ){
            logger.severe( String.valueOf( ioe ));
        }catch( final Exception e ){
            System.err.println( String.valueOf( e ));
            throw e;
        }
        return;
    }
    
    private final RuntimeConfig config;
    
    public BeatTheLucySeven(final RuntimeConfig config){
        this.config = config;
        return;
    }
    
    public void run(){
        logger.entering( "BeatTheLucySeven" , "run" );
        logger.info( String.valueOf( this.config ));
        try{
            try( final java.nio.channels.Selector selector = java.nio.channels.Selector.open() ){

                final SelectionKey controlKey; // Selection key of control channel
                synchronized( this.config ){
                    assert config.sourceChannel != null ;
                    controlKey = config.sourceChannel.register( selector , java.nio.channels.SelectionKey.OP_READ , null );
                }

                for(;;){
                    {
                        final var keys = selector.keys();
                        synchronized( keys ){
                            if( keys.isEmpty() ){
                                break;
                            }
                        }
                    }
                    if( 0 < selector.select() ){
                        do{
                            final var ite = selector.selectedKeys().iterator();
                            while( ite.hasNext() ){
                                final var key = ite.next();
                                ite.remove();
                                if( key == controlKey ){
                                    try{
                                        final java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate( 1 );
                                        synchronized( this.config ){
                                            if( -1 == config.sourceChannel.read( b ) ){
                                                key.cancel();
                                            }
                                        }
                                    }catch( final java.io.IOException ioe ){
                                        key.cancel();
                                    }
                                }
                            }
                        }while( 0 < selector.selectNow() );
                    }
                }
                
                synchronized( this.config ){
                    try{
                        config.sourceChannel.close();
                        config.sourceChannel = null;
                    }catch( final java.io.IOException ioe ){
                        logger.severe( String.valueOf( ioe ) );
                    }
                }
                
            }
        }catch( final java.io.IOException ioe ){
            logger.severe( String.valueOf( ioe ));
        }
    }

    public static final void main( final String args[] ){
        final RuntimeConfig config = new RuntimeConfig();
        entry_point( config );
        return;
    }
}
