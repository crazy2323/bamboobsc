/* 
 * Copyright 2012-2016 bambooCORE, greenstep of copyright Chen Xin Nien
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * -----------------------------------------------------------------------
 * 
 * author: 	Chen Xin Nien
 * contact: chen.xin.nien@gmail.com
 * 
 */
package com.netsteadfast.greenstep.support;

import javax.servlet.ServletContextEvent;

import org.apache.camel.CamelContext;
import org.apache.camel.spring.SpringCamelContext;

import com.netsteadfast.greenstep.base.AppContext;
import com.netsteadfast.greenstep.base.model.ContextInitializedAndDestroyedBean;

/**
 * for Test
 * test to start HelloRouteBuilder with core-web / applicationContext-STANDARD-ESB.xml
 *
 */
public class TestHelloCamelStartForContextInitialized extends ContextInitializedAndDestroyedBean {
	private static final long serialVersionUID = -8411250949691213024L;

	@Override
	public void execute(ServletContextEvent event) throws Exception {
		CamelContext context = new SpringCamelContext( AppContext.getApplicationContext() );
		context.start();		
	}

}
