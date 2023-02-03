package net.iogilab.example;

import java.lang.*;
/**

   
   $ java -Djava.util.logging.config.file=logging.properties  -cp target/classes net.iogilab.example.ObjectWatch

*/
public final class ObjectWatch{
    private static final java.util.logging.Logger logger =
        java.util.logging.Logger.getLogger("net.iogilab.example.ObjectWatch");

    public static final void entry(){
        final Object obj = new Object();
        synchronized( obj ){

            // 今 この スレッドが obj のロックを持っている
            
            final Thread thread = new Thread( ()->{
                    logger.finest( "BEGIN synchronized( obj )" );
                    // ここで obj のロックを確保しようとする。 下の obj.wait() がロックを放棄するまでブロックされる
                    synchronized( obj ){ 
                        logger.finest( "ENTER obj.notify()" );
                        obj.notify(); // obj.wait() からのリターンを要求する（が、今objの所有権を持っているのはこちら側なので、実行は待機）
                        logger.finest( "RETURN obj.notify()" );
                    } // ここで obj のロックが開放されるので、あちら側のスレッドが実行可能状態に遷移する。
                    logger.finest( "LEAVE synchronized( obj )" );
                    return;
                });
            
            thread.start();

            try{
                logger.finest( "ENTER obj.wait()" );
                // wait() に入ると、一時的に obj のロックを放棄してスレッドを停止状態になる
                obj.wait();
                // wait() から戻る前に、 obj のロックをを確保してスレッドが実行状態になる
                logger.finest( "RETURN obj.wait()" );
            }catch( final InterruptedException ie ){
                logger.severe( String.valueOf( ie ));
            }
            try{
                thread.join();
            }catch( final InterruptedException ie ){
                logger.severe( String.valueOf( ie ));
            }
        }
        return;
    }
    
    public static final void main( final String args[] ){
        logger.info( "ObjectWatch" );
        entry();
        MultipleObjects.entry();

        final var l = new Object(){ // some() というメソッドをもった Object型を継承したなにか
                public int some(){
                    return 10;
                }
            };
        l.some(); // 型がvar で取れるので some() はそのまま呼び出せる。
        return;
    }
}
