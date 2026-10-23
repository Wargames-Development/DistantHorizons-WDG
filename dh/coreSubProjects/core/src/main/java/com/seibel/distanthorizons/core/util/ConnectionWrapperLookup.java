package com.seibel.distanthorizons.core.util;

import java.util.Map;
import java.util.function.Function;

/** Logout must find an existing wrapper, never create one for a rejected connection. */
public final class ConnectionWrapperLookup {
    private ConnectionWrapperLookup() { }
    public static <P,C,W> W existing(P player,C connection,Map<C,W> wrappers,Function<W,P> owner) {
        if(player==null)return null;
        if(connection!=null) {
            W wrapper=wrappers.get(connection);
            if(wrapper!=null && owner.apply(wrapper)==player)return wrapper;
        }
        // Crucible may clear the player's handler before firing the logout event.
        // A successfully joined player's wrapper still retains the original handler.
        for(W wrapper:wrappers.values()) {
            if(wrapper!=null && owner.apply(wrapper)==player)return wrapper;
        }
        return null;
    }
}
