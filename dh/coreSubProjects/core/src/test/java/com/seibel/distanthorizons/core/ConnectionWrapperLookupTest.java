package com.seibel.distanthorizons.core;

import com.google.common.collect.MapMaker;
import com.seibel.distanthorizons.core.util.ConnectionWrapperLookup;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.concurrent.ConcurrentMap;

public class ConnectionWrapperLookupTest {
    private static final class Wrapper {
        final Object player;
        Wrapper(Object player){this.player=player;}
    }
    private ConcurrentMap<Object,Wrapper> cache() {
        // Same null-rejecting weak-key/value cache used by ServerPlayerWrapper.
        return new MapMaker().weakKeys().weakValues().makeMap();
    }
    @Test public void rejectedConnectionWithNoHandlerDoesNotTouchNullKeyOrCreateWrapper() {
        ConcurrentMap<Object,Wrapper> map=cache();Object player=new Object();
        assertNull(ConnectionWrapperLookup.existing(player,null,map,w->w.player));
        assertTrue(map.isEmpty());
    }
    @Test public void joinedPlayerCanStillBeCleanedUpAfterHandlerWasCleared() {
        ConcurrentMap<Object,Wrapper> map=cache();Object player=new Object(),connection=new Object();Wrapper wrapper=new Wrapper(player);
        map.put(connection,wrapper);
        assertSame(wrapper,ConnectionWrapperLookup.existing(player,null,map,w->w.player));
        assertEquals(1,map.size());
    }
    @Test public void normalDisconnectFindsSameWrapperWithoutCreatingAnother() {
        ConcurrentMap<Object,Wrapper> map=cache();Object player=new Object(),connection=new Object();Wrapper wrapper=new Wrapper(player);
        map.put(connection,wrapper);
        assertSame(wrapper,ConnectionWrapperLookup.existing(player,connection,map,w->w.player));
        assertEquals(1,map.size());
    }
    @Test public void unknownConnectionAndNullPlayerCannotDisconnectAnotherPlayer() {
        ConcurrentMap<Object,Wrapper> map=cache();Object owner=new Object(),connection=new Object();Wrapper wrapper=new Wrapper(owner);map.put(connection,wrapper);
        assertNull(ConnectionWrapperLookup.existing(new Object(),null,map,w->w.player));
        assertNull(ConnectionWrapperLookup.existing(new Object(),connection,map,w->w.player));
        assertNull(ConnectionWrapperLookup.existing(null,connection,map,w->w.player));
        assertSame(wrapper,map.get(connection));
    }
    @Test public void connectionIdentityTakesPrecedenceForCurrentSession() {
        ConcurrentMap<Object,Wrapper> map=cache();Object player=new Object(),oldConnection=new Object(),currentConnection=new Object();
        Wrapper oldWrapper=new Wrapper(player),currentWrapper=new Wrapper(player);map.put(oldConnection,oldWrapper);map.put(currentConnection,currentWrapper);
        assertSame(currentWrapper,ConnectionWrapperLookup.existing(player,currentConnection,map,w->w.player));
        assertNull(ConnectionWrapperLookup.existing(new Object(),currentConnection,map,w->w.player));
        assertEquals(2,map.size());
    }
}
