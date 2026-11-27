package com.seibel.distanthorizons.core;

import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.IndexBufferBuilder;
import com.seibel.distanthorizons.core.util.objects.pooling.PhantomArrayList.PhantomArrayListCheckout;
import com.seibel.distanthorizons.core.util.objects.pooling.PhantomArrayList.PhantomArrayListPool;
import java.nio.ByteBuffer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class IndexBufferBuilderTest
{
	@Test
	public void compactIndicesKeepTriangleOrderThroughTheUnsignedLimit()
	{
		PhantomArrayListPool pool = new PhantomArrayListPool("test indices");
		int maxQuads = IndexBufferBuilder.MAX_SHORT_QUADS;
		try (PhantomArrayListCheckout checkout = pool.checkoutByteBuffers(2))
		{
			ByteBuffer compact = IndexBufferBuilder.populateBuffer(checkout, 0, maxQuads, true);
			ByteBuffer wide = IndexBufferBuilder.populateBuffer(checkout, 1, maxQuads + 1);
			assertEquals(maxQuads * 6 * Short.BYTES, compact.remaining());
			while (compact.hasRemaining())
			{
				assertEquals(wide.getInt(), Short.toUnsignedInt(compact.getShort()));
			}
			assertEquals(65536, wide.getInt());
			assertThrows(IllegalArgumentException.class,
					() -> IndexBufferBuilder.populateBuffer(checkout, 0, maxQuads + 1, true));
		}
	}
}
