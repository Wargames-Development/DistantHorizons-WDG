package com.seibel.distanthorizons.forge17.actors;

import java.util.*;

/** Missing revisions precede repairs; oldest repairs precede nearer repeats. */
final class AppearanceDeliveryOrder {
    static List<PlayerFrame.Pose> ordered(List<PlayerFrame.Pose> poses,Map<UUID,Long> known,Map<UUID,Long> sent) {
        List<PlayerFrame.Pose> order=new ArrayList<>(poses);
        order.sort((a,b)-> {
            boolean missingA=known.getOrDefault(a.uuid,0L)!=a.appearanceRevision;
            boolean missingB=known.getOrDefault(b.uuid,0L)!=b.appearanceRevision;
            if(missingA!=missingB) return missingA?-1:1;
            return Long.compare(sent.getOrDefault(a.uuid,Long.MIN_VALUE),sent.getOrDefault(b.uuid,Long.MIN_VALUE));
        });
        return order;
    }
}
