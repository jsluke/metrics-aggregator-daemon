/**
 * Copyright 2014 Groupon.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.arpnetworking.utility;

import java.util.Set;

/**
 * This class provides searchable access to classes by interface.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public interface InterfaceDatabase {

    /**
     * Retrieve the <code>Set</code> of classes with the implemented interface.
     *
     * @param <T> The interface type.
     * @param interfaceClass The interface class to search for.
     * @return The <code>Set</code> of classes that are implementing the
     * specified interface.
     */
    <T> Set<Class<? extends T>> findClassesWithInterface(Class<T> interfaceClass);
}
