package io.doeasy.redis.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 
 * @author kriswang
 *
 * @param <T>
 */
public class CircularList<T> implements Iterable<T> {

	private final ReentrantLock lock = new ReentrantLock();
	private final List<T> collection;
	private int currentIndex;

	public CircularList(Collection<T> iterator) {
		this.collection = new ArrayList<T>(iterator);
	}

	public CircularList(@SuppressWarnings("unchecked") T... items) {
		this(Arrays.asList(items));
	}
	
	public int size() {
		return this.collection.size();
	}
	
	public boolean empty() {
		return this.collection.size() == 0;
	}
	
	public void add(T t) {
		try {
			this.lock.lock();
			this.collection.add(t);
		} finally {
			this.lock.unlock();
		}
	}
	
	public void remove(T t) {
		try {
			this.lock.lock();
			for(T c : collection) {
				if(c.equals(t)) {
					this.collection.remove(t);
				}
			}
		} finally {
			this.lock.unlock();
		}
	}
	
	public T next() {
		try {
			this.lock.lock();
			
			T item = this.collection.get(this.currentIndex);
			
			if(this.currentIndex >= (this.collection.size() - 1)) {
				this.currentIndex = 0;
			} else {
				this.currentIndex++;
			}
			
			return item;
		} finally {
			this.lock.unlock();
		}
	}

	@Override
	public Iterator<T> iterator() {
		return this.collection.iterator();
	}

}
