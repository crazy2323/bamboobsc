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
package com.netsteadfast.greenstep.base.interceptor;

import java.io.PrintWriter;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.StrutsStatics;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.netsteadfast.greenstep.base.AppContext;
import com.netsteadfast.greenstep.base.Constants;
import com.netsteadfast.greenstep.base.model.AccountObj;
import com.netsteadfast.greenstep.base.model.DefaultResult;
import com.netsteadfast.greenstep.base.model.YesNo;
import com.netsteadfast.greenstep.base.sys.IUSessLogHelper;
import com.netsteadfast.greenstep.base.sys.USessLogHelperImpl;
import com.netsteadfast.greenstep.base.sys.UserAccountHttpSessionSupport;
import com.netsteadfast.greenstep.base.sys.UserCurrentCookie;
import com.netsteadfast.greenstep.po.hbm.TbAccount;
import com.netsteadfast.greenstep.service.IAccountService;
import com.netsteadfast.greenstep.sys.GreenStepBaseUsernamePasswordToken;
import com.netsteadfast.greenstep.util.ApplicationSiteUtils;
import com.netsteadfast.greenstep.vo.AccountVO;
import com.opensymphony.xwork2.ActionContext;
import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.interceptor.AbstractInterceptor;

@Component("greenstep.web.interceptor.UserLoginInterceptor")
@Scope("prototype")
public class UserLoginInterceptor extends AbstractInterceptor {
	private static final long serialVersionUID = -115850491560281565L;
	private IUSessLogHelper uSessLogHelper;
	private IAccountService<AccountVO, TbAccount, String> accountService;	
	private AccountObj accountObj = null;
	
	@SuppressWarnings("unchecked")
	public UserLoginInterceptor() {
		super();
		uSessLogHelper=new USessLogHelperImpl();
		accountService = (IAccountService<AccountVO, TbAccount, String>)AppContext.getBean("core.service.AccountService");
	}
		
	@Override
	public String intercept(ActionInvocation actionInvocation) throws Exception {
		ActionContext actionContext=actionInvocation.getInvocationContext();  
		Map<String, Object> session=actionContext.getSession();  
		this.accountObj = (AccountObj)session.get(Constants.SESS_ACCOUNT);
		boolean fromCookieCheckOrRetySubjectLogin = false;
		boolean getUserCurrentCookieFail = false; // 有 sysCurrentId 的 cookie, 但用這個cookie資料count tb_sys_usess 又與 core-web 的資料不符
		String contextPath = ServletActionContext.getServletContext().getContextPath();
		if (!contextPath.endsWith( ApplicationSiteUtils.getContextPathFromMap(Constants.getMainSystem()) ) ) {
			/**
			 * 1. 先用admin登入
			 * 2. 登出admin 改用 tester登入
			 * 這樣的話 gsbsc-web 的 http-session 還是admin , 所以非core-web 要檢查當前CURRENT cookie 中的帳戶是否與 gsbsc-web 一樣
			 * 要是不同的話就讓這個 http-session 失效掉
			 */
			this.invalidCurrentSessionForDifferentAccount(actionContext);					
			
			SecurityUtils.setSecurityManager( (DefaultSecurityManager)AppContext.getBean("securityManager") );
			Subject subject = SecurityUtils.getSubject();
			if (accountObj==null) {
				fromCookieCheckOrRetySubjectLogin = getUserCurrentCookie(actionContext);
				if (!fromCookieCheckOrRetySubjectLogin 
						&& UserCurrentCookie.foundCurrent( (HttpServletRequest)actionContext.get(StrutsStatics.HTTP_REQUEST) ) ) {
					 // 有 sysCurrentId 的 cookie, 但用這個cookie資料count tb_sys_usess 又與 core-web 的資料不符
					getUserCurrentCookieFail = true;
				}				
			}			
			if (accountObj!=null && !subject.isAuthenticated()) {
				fromCookieCheckOrRetySubjectLogin = true;
			}			
		}
		if (accountObj!=null && !StringUtils.isBlank(accountObj.getAccount()) ) {
			if (uSessLogHelper.countByAccount(accountObj.getAccount())<1) {
				return this.redirectLogin(session, getUserCurrentCookieFail);
			}			
			if (fromCookieCheckOrRetySubjectLogin) { // core-web 有 session了, 但gsbsc-web 沒有session, 所以產生gsbsc-web 的 http session		
				SecurityUtils.setSecurityManager( (DefaultSecurityManager)AppContext.getBean("securityManager") );
				Subject subject = SecurityUtils.getSubject();
				GreenStepBaseUsernamePasswordToken token = new GreenStepBaseUsernamePasswordToken();
				token.setRememberMe(false);
				token.setCaptcha("");
				token.setUsername(accountObj.getAccount());		
				token.setPassword( ((AccountVO)accountObj).getPassword().toCharArray() );
				if (!subject.isAuthenticated()) {
					subject.login(token);
				}		
				UserAccountHttpSessionSupport.create(actionContext, accountObj);
			}
			return actionInvocation.invoke();
		}	
		return this.redirectLogin(session, getUserCurrentCookieFail);
	}
	
	/**
	 * 1. 先用admin登入
	 * 2. 登出admin 改用 tester登入
	 * 這樣的話 gsbsc-web 的 http-session 還是admin , 所以非core-web用tester登入的session , 要檢查當前CURRENT cookie 中的帳戶是否與 gsbsc-web 一樣
	 * 要是不同的話就讓這個 http-session 失效掉
	 *  
	 * @param actionContext
	 * @throws Exception
	 */
	private void invalidCurrentSessionForDifferentAccount(ActionContext actionContext) throws Exception {
		if (this.accountObj == null) {
			return;
		}
		Map<String, String> dataMap = UserCurrentCookie.getCurrentData( (HttpServletRequest)actionContext.get(StrutsStatics.HTTP_REQUEST) );
		String account = StringUtils.defaultString( dataMap.get("account") );
		if (StringUtils.isBlank(account)) {
			return;
		}
		if (this.accountObj.getAccount().equals(account)) {
			return;
		}
		this.accountObj = null;		
		UserAccountHttpSessionSupport.remove(actionContext.getSession());
		SecurityUtils.getSubject().logout();
	}
	
	/**
	 * 取出core-web 登入後產生的cookie, 這個cookie放了 account 與 current-id
	 * 拿這兩個去 TB_SYS_USESS 查看有沒有在core-web有登入過
	 * 如果有在core-web登入, 產生 AccountVO 與回傳 true
	 * 
	 * @param actionContext
	 * @return
	 * @throws Exception
	 */
	private boolean getUserCurrentCookie(ActionContext actionContext) throws Exception {
		Map<String, String> dataMap = UserCurrentCookie.getCurrentData( (HttpServletRequest)actionContext.get(StrutsStatics.HTTP_REQUEST) );
		String account = StringUtils.defaultString( dataMap.get("account") );
		String currentId = StringUtils.defaultString( dataMap.get("currentId") );
		//String sessionId = StringUtils.defaultString( dataMap.get("sessionId") );
		if (StringUtils.isBlank(account) || currentId.length()!=36 /*|| StringUtils.isBlank(sessionId)*/ ) { 	
			return false;
		}
		// 發現有時 UserCurrentCookie 寫入的 sessionId 與當前 sessionId 會不一樣
		if (this.uSessLogHelper.countByCurrent(account, currentId) >0 ) { // this.uSessLogHelper.countByCurrent(account, currentId, sessionId) >0 		 	
			accountObj = new AccountVO();
			((AccountVO)accountObj).setAccount(account);
			DefaultResult<AccountVO> result = this.accountService.findByUK( ((AccountVO)accountObj) );
			if (result.getValue()==null) {
				accountObj = null;
			} else {
				accountObj = result.getValue();
			}			
		}					
		return ( accountObj!=null && !StringUtils.isBlank(accountObj.getAccount()) );
	}
	
	private String redirectLogin(Map<String, Object> session, boolean getUserCurrentCookieFail) throws Exception {
		SecurityUtils.getSubject().logout();		
		if (session!=null) {
			UserAccountHttpSessionSupport.remove(session);
		}		
		String header = ServletActionContext.getRequest().getHeader("X-Requested-With");
		String isDojoContentPaneXhrLoad = ServletActionContext.getRequest().getParameter(Constants.IS_DOJOX_CONTENT_PANE_XHR_LOAD);	
		if ("XMLHttpRequest".equalsIgnoreCase(header) && !YesNo.YES.equals(isDojoContentPaneXhrLoad) ) {
			PrintWriter printWriter = ServletActionContext.getResponse().getWriter();
			printWriter.print(Constants.NO_LOGIN_JSON_DATA);
            printWriter.flush();
            printWriter.close();
			return null;
		}				
		if (YesNo.YES.equals(isDojoContentPaneXhrLoad)) {
			if (getUserCurrentCookieFail) {
				return Constants._S2_RESULT_LOGOUT_AGAIN;
			}
			return Constants._S2_RESULT_LOGIN_AGAIN;
		}
		if (getUserCurrentCookieFail) {
			return "logout";
		}
		return "login";		
	}

}
