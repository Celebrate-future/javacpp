/*
 * Copyright (C) 2018-2019 Samuel Audet
 *
 * Licensed either under the Apache License, Version 2.0, or (at your option)
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation (subject to the "Classpath" exception),
 * either version 2, or any later version (collectively, the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     http://www.gnu.org/licenses/
 *     http://www.gnu.org/software/classpath/license.html
 *
 * or as provided in the LICENSE.txt file that accompanied this code.
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bytedeco.javacpp;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import org.bytedeco.javacpp.tools.Logger;

/**
 * {@link Pointer} objects attach themselves automatically on {@link Pointer#init} to the first {@link PointerScope}
 * found in {@link #scopeStack} that they can to based on the classes found in {@link #forClasses}. The user can then
 * call {@link #deallocate()}, or rely on {@link #close()} to deallocate in a timely fashion all attached Pointer objects,
 * instead of relying on the garbage collector.
 */
public class PointerScope implements AutoCloseable {
    private static final Logger logger = Logger.create(PointerScope.class);

    /** A thread-local stack of {@link PointerScope} objects. Pointer objects attach themselves
     * automatically on {@link Pointer#init} to the first one they can to on the stack. */
    static final ThreadLocal<Deque<PointerScope>> scopeStack = new ThreadLocal<Deque<PointerScope>>() {
        @Override protected Deque initialValue() {
            return new ArrayDeque<PointerScope>();
        }
    };

    /** Returns {@code scopeStack.get().peek()}, the last opened scope not yet closed. */
    public static PointerScope getInnerScope() {
        return scopeStack.get().peek();
    }

    /** Returns {@code scopeStack.get().iterator()}, all scopes not yet closed. */
    public static Iterator<PointerScope> getScopeIterator() {
        return scopeStack.get().iterator();
    }

    /** The stack keeping references to attached {@link Pointer} objects. */
    Deque<Pointer> pointerStack = new ArrayDeque<Pointer>();

    /** When true, {@link #deallocate()} gets called on {@link #close()}. */
    boolean deallocateOnClose = true;

    /** When not empty, indicates the classes of objects that are allowed to be attached. */
    Class<? extends Pointer>[] forClasses = null;

    /** Calls {@code this(true)}. */
    public PointerScope() {
        this(true);
    }

    /** Initializes {@link #deallocateOnClose} and {@link #forClasses}, and pushes itself on the {@link #scopeStack}. */
    public PointerScope(boolean deallocateOnClose, Class<? extends Pointer>... forClasses) {
        if (logger.isDebugEnabled()) {
            logger.debug("Opening " + this);
        }
        this.deallocateOnClose = deallocateOnClose;
        this.forClasses = forClasses;
        scopeStack.get().push(this);
    }

    /** Sets {@link #deallocateOnClose} and returns this Scope. */
    public PointerScope deallocateOnClose(boolean deallocateOnClose) {
        this.deallocateOnClose = deallocateOnClose;
        return this;
    }

    /** Returns {@link #deallocateOnClose}. */
    public boolean deallocateOnClose() {
        return deallocateOnClose;
    }

    public Class<? extends Pointer>[] forClasses() {
        return forClasses;
    }

    /** Pushes the Pointer onto the {@link #pointerStack} of this Scope.
     * @throws IllegalArgumentException when it is not an instance of a class in {@link #forClasses}.*/
    public PointerScope attach(Pointer p) {
        if (logger.isDebugEnabled()) {
            logger.debug("Attaching " + p + " to " + this);
        }
        if (forClasses != null && forClasses.length > 0) {
            boolean found = false;
            for (Class<? extends Pointer> c : forClasses) {
                if (c != null && c.isInstance(p)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IllegalArgumentException(p + " is not an instance of a class in forClasses: " + Arrays.toString(forClasses));
            }
        }
        pointerStack.push(p);
        return this;
    }

    /** Removes the Pointer from the {@link #pointerStack} of this Scope. */
    public PointerScope detach(Pointer p) {
        if (logger.isDebugEnabled()) {
            logger.debug("Detaching " + p + " from " + this);
        }
        pointerStack.remove(p);
        return this;
    }

    /** Calls {@link #deallocate()} when {@link #deallocateOnClose} is true,
     * and removes itself from {@link #scopeStack}. */
    @Override public void close() {
        if (logger.isDebugEnabled()) {
            logger.debug("Closing " + this);
        }
        if (deallocateOnClose()) {
            deallocate();
        }
        scopeStack.get().remove(this);
    }

    /** Calls {@link Pointer#deallocate()} on all attached pointers, 
     * as they are popped off the {@link #pointerStack}. */
    public void deallocate() {
        if (logger.isDebugEnabled()) {
            logger.debug("Deallocating " + this);
        }
        while (pointerStack.size() > 0) {
            pointerStack.pop().deallocate();
        }
        pointerStack = null;
    }
}
