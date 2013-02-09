package org.jolokia.restrictor;

/*
 * Copyright 2009-2013 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


/**
 * A restrictor which simply allows everything. Used, when no jolokia-access.xml is
 * present.
 *
 * @author roland
 * @since Jul 28, 2009
 */
public class AllowAllRestrictor extends AbstractConstantRestrictor {

    /** {@inheritDoc} */
    public AllowAllRestrictor() {
        super(true);
    }
}
