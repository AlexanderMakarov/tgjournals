package com.aleksandrmakarov.journals.config;

import com.aleksandrmakarov.journals.repository.JournalRepository;
import com.aleksandrmakarov.journals.repository.QuestionRepository;
import com.aleksandrmakarov.journals.repository.SessionRepository;
import com.aleksandrmakarov.journals.repository.UserRepository;
import org.springframework.aop.SpringProxy;
import org.springframework.aop.framework.Advised;
import org.springframework.aot.hint.ProxyHints;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.core.DecoratingProxy;

public class RepositoryRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		ProxyHints proxyHints = hints.proxies();

		proxyHints.registerJdkProxy(UserRepository.class, SpringProxy.class, Advised.class, DecoratingProxy.class);

		proxyHints.registerJdkProxy(SessionRepository.class, SpringProxy.class, Advised.class, DecoratingProxy.class);

		proxyHints.registerJdkProxy(JournalRepository.class, SpringProxy.class, Advised.class, DecoratingProxy.class);

		proxyHints.registerJdkProxy(QuestionRepository.class, SpringProxy.class, Advised.class, DecoratingProxy.class);
	}
}
