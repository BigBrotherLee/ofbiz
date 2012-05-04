/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.ofbiz.base.crypto;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("-crypt")) {
            System.out.println(HashCrypt.cryptUTF8(args[1], null, args[2]));
        } else if (args[0].equals("-digest")) {
            @SuppressWarnings("deprecation")
            String digest = HashCrypt.getDigestHash(args[1]);
            System.out.println(digest);
        }
    }
}
