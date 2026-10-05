/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.core.network.event;

import com.seibel.distanthorizons.core.network.messages.AbstractNetworkMessage;

import java.util.function.Consumer;
import java.util.HashSet;
import java.util.Set;

/** 
 * Provides a way to register network message handlers which are expected to be removed later. <br><br>
 * 
 * In other words, listeners can be added to this {@link AbstractNetworkEventSource} and when
 * you no longer need any of those listeners you can {@link ScopedNetworkEventSource#close()}
 * this handler to remove all of them.
 */
public final class ScopedNetworkEventSource extends AbstractNetworkEventSource
{
	public final AbstractNetworkEventSource parent;
	private volatile boolean isClosed = false;
	private final Set<Class<?>> forwardedMessageClasses = new HashSet<>();
	
	private final Consumer<AbstractNetworkMessage> actualHandleMessageStable = message ->
	{
		if (!this.isClosed) { this.handleMessage(message); }
	};
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public ScopedNetworkEventSource(AbstractNetworkEventSource parent) { this.parent = parent; }
	
	
	
	//==================//
	// message handlers //
	//==================//
	
	@Override
	public synchronized <T extends AbstractNetworkMessage> void registerHandler(Class<T> handlerClass, Consumer<T> handlerImplementation)
	{
		if (this.isClosed)
		{
			return;
		}
		
		super.registerHandler(this, handlerClass, handlerImplementation);
		// One forwarder dispatches all local handlers for this message type.
		if (this.forwardedMessageClasses.add(handlerClass))
		{
			//noinspection unchecked
			this.parent.registerHandler(this, handlerClass, (Consumer<T>) this.actualHandleMessageStable);
		}
	}
	
	
	
	//==========//
	// shutdown //
	//==========//
	
	@Override
	public synchronized void close()
	{
		this.isClosed = true;
		this.parent.removeAllHandlers(this);
		this.forwardedMessageClasses.clear();
		super.close();
	}
	
}
