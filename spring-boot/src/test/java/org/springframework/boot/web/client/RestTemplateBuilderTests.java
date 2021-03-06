/*
 * Copyright 2012-2016 the original author or authors.
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
 */

package org.springframework.boot.web.client;

import java.util.Collections;
import java.util.Set;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.support.BasicAuthorizationInterceptor;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriTemplateHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests for {@link RestTemplateBuilder}.
 *
 * @author Stephane Nicoll
 * @author Phillip Webb
 */
public class RestTemplateBuilderTests {

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	private RestTemplateBuilder builder = new RestTemplateBuilder();

	@Mock
	private HttpMessageConverter<Object> messageConverter;

	@Before
	public void setup() {
		MockitoAnnotations.initMocks(this);
	}

	@Test
	public void createWhenCustomizersAreNullShouldThrowException() throws Exception {
		this.thrown.expect(IllegalArgumentException.class);
		this.thrown.expectMessage("Customizers must not be null");
		RestTemplateCustomizer[] customizers = null;
		new RestTemplateBuilder(customizers);
	}

	@Test
	public void createWithCustomizersShouldApplyCustomizers() throws Exception {
		RestTemplateCustomizer customizer = mock(RestTemplateCustomizer.class);
		RestTemplate template = new RestTemplateBuilder(customizer).build();
		verify(customizer).customize(template);
	}

	@Test
	public void buildShouldDetectRequestFactory() throws Exception {
		RestTemplate restTemplate = this.builder.build();
		assertThat(restTemplate.getRequestFactory())
				.isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
	}

	@Test
	public void detectRequestFactoryWhenFalseShouldDisableDetection() throws Exception {
		RestTemplate restTemplate = this.builder.detectRequestFactory(false).build();
		assertThat(restTemplate.getRequestFactory())
				.isInstanceOf(SimpleClientHttpRequestFactory.class);
	}

	@Test
	public void rootUriShouldApply() throws Exception {
		RestTemplate restTemplate = this.builder.rootUri("http://example.com").build();
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("http://example.com/hello")).andRespond(withSuccess());
		restTemplate.getForEntity("/hello", String.class);
		server.verify();
	}

	@Test
	public void rootUriShouldApplyAfterUriTemplateHandler() throws Exception {
		UriTemplateHandler uriTemplateHandler = mock(UriTemplateHandler.class);
		RestTemplate template = this.builder.uriTemplateHandler(uriTemplateHandler)
				.rootUri("http://example.com").build();
		UriTemplateHandler handler = template.getUriTemplateHandler();
		handler.expand("/hello");
		assertThat(handler).isInstanceOf(RootUriTemplateHandler.class);
		verify(uriTemplateHandler).expand("http://example.com/hello");
	}

	@Test
	public void messageConvertersWhenConvertersAreNullShouldThrowException()
			throws Exception {
		this.thrown.expect(IllegalArgumentException.class);
		this.thrown.expectMessage("MessageConverters must not be null");
		this.builder.messageConverters((HttpMessageConverter<?>[]) null);
	}

	@Test
	public void messageConvertersCollectionWhenConvertersAreNullShouldThrowException()
			throws Exception {
		this.thrown.expect(IllegalArgumentException.class);
		this.thrown.expectMessage("MessageConverters must not be null");
		this.builder.messageConverters((Set<HttpMessageConverter<?>>) null);
	}

	@Test
	public void messageConvertersShouldApply() throws Exception {
		RestTemplate template = this.builder.messageConverters(this.messageConverter)
				.build();
		assertThat(template.getMessageConverters()).containsOnly(this.messageConverter);
	}

	@Test
	public void messageConvertersShouldReplaceExisting() throws Exception {
		RestTemplate template = this.builder
				.messageConverters(new ResourceHttpMessageConverter())
				.messageConverters(Collections.singleton(this.messageConverter)).build();
		assertThat(template.getMessageConverters()).containsOnly(this.messageConverter);
	}

	@Test
	public void additionalMessageConvertersWhenConvertersAreNullShouldThrowException()
			throws Exception {
		this.thrown.expect(IllegalArgumentException.class);
		this.thrown.expectMessage("MessageConverters must not be null");
		this.builder.additionalMessageConverters((HttpMessageConverter<?>[]) null);
	}

	@Test
	public void additionalMessageConvertersCollectionWhenConvertersAreNullShouldThrowException()
			throws Exception {
		this.thrown.expect(IllegalArgumentException.class);
		this.thrown.expectMessage("MessageConverters must not be null");
		this.builder.additionalMessageConverters((Set<HttpMessageConverter<?>>) null);
	}

	@Test
	public void additionalMessageConvertersShouldAddToExisting() throws Exception {
		HttpMessageConverter<?> resourceConverter = new ResourceHttpMessageConverter();
		RestTemplate template = this.builder.messageConverters(resourceConverter)
				.additionalMessageConverters(this.messageConverter).build();
		assertThat(template.getMessageConverters()).containsOnly(resourceConverter,
				this.messageConverter);
	}

	@Test
	public void defaultMessageConvertersShouldSetDefaultList() throws Exception {
		RestTemplate template = new RestTemplate(
				Collections.<HttpMessageConverter<?>>singletonList(
						new StringHttpMessageConverter()));
		this.builder.defaultMessageConverters().configure(template);
		assertThat(template.getMessageConverters())
				.hasSameSizeAs(new RestTemplate().getMessageConverters());
	}

	@Test
	public void defaultMessageConvertersShouldClearExisting() throws Exception {
		RestTemplate template = new RestTemplate(
				Collections.<HttpMessageConverter<?>>singletonList(
						new StringHttpMessageConverter()));
		this.builder.additionalMessageConverters(this.messageConverter)
				.defaultMessageConverters().configure(template);
		assertThat(template.getMessageConverters())
				.hasSameSizeAs(new RestTemplate().getMessageConverters());
	}

	@Test
	public void requestFactoryClassWhenFactoryIsNullShouldThrowException()
			throws Exception {
		this.thrown.expect(IllegalArgumentException.class);
		this.thrown.expectMessage("RequestFactory must not be null");
		this.builder.requestFactory((Class<ClientHttpRequestFactory>) null);
	}

	@Test
	public void requestFactoryClassShouldApply() throws Exception {
		RestTemplate template = this.builder
				.requestFactory(SimpleClientHttpRequestFactory.class).build();
		assertThat(template.getRequestFactory())
				.isInstanceOf(SimpleClientHttpRequestFactory.class);
	}

	@Test
	public void requestFactoryWhenFactoryIsNullShouldThrowException() throws Exception {
		this.thrown.expect(IllegalArgumentException.class);
		this.thrown.expectMessage("RequestFactory must not be null");
		this.builder.requestFactory((ClientHttpRequestFactory) null);
	}

	@Test
	public void requestFactoryShouldApply() throws Exception {
		ClientHttpRequestFactory requestFactory = mock(ClientHttpRequestFactory.class);
		RestTemplate template = this.builder.requestFactory(requestFactory).build();
		assertThat(template.getRequestFactory()).isSameAs(requestFactory);
	}

	@Test
	public void uriTemplateHandlerWhenHandlerIsNullShouldThrowException()
			throws Exception {
		this.thrown.expect(IllegalArgumentException.class);
		this.thrown.expectMessage("UriTemplateHandler must not be null");
		this.builder.uriTemplateHandler(null);
	}

	@Test
	public void uriTemplateHandlerShouldApply() throws Exception {
		UriTemplateHandler uriTemplateHandler = mock(UriTemplateHandler.class);
		RestTemplate template = this.builder.uriTemplateHandler(uriTemplateHandler)
				.build();
		assertThat(template.getUriTemplateHandler()).isSameAs(uriTemplateHandler);
	}

	@Test
	public void errorHandlerWhenHandlerIsNullShouldThrowException() throws Exception {
		this.thrown.expect(IllegalArgumentException.class);
		this.thrown.expectMessage("ErrorHandler must not be null");
		this.builder.errorHandler(null);
	}

	@Test
	public void errorHandlerShouldApply() throws Exception {
		ResponseErrorHandler errorHandler = mock(ResponseErrorHandler.class);
		RestTemplate template = this.builder.errorHandler(errorHandler).build();
		assertThat(template.getErrorHandler()).isSameAs(errorHandler);
	}

	@Test
	public void basicAuthorizationShouldApply() throws Exception {
		RestTemplate template = this.builder.basicAuthorization("spring", "boot").build();
		ClientHttpRequestInterceptor interceptor = template.getInterceptors().get(0);
		assertThat(interceptor).isInstanceOf(BasicAuthorizationInterceptor.class);
		assertThat(interceptor).extracting("username").containsExactly("spring");
		assertThat(interceptor).extracting("password").containsExactly("boot");
	}

	@Test
	public void customizersWhenCustomizersAreNullShouldThrowException() throws Exception {
		this.thrown.expect(IllegalArgumentException.class);
		this.thrown.expectMessage("RestTemplateCustomizers must not be null");
		this.builder.customizers((RestTemplateCustomizer[]) null);
	}

	@Test
	public void customizersCollectionWhenCustomizersAreNullShouldThrowException()
			throws Exception {
		this.thrown.expect(IllegalArgumentException.class);
		this.thrown.expectMessage("RestTemplateCustomizers must not be null");
		this.builder.customizers((Set<RestTemplateCustomizer>) null);
	}

	@Test
	public void customizersShouldApply() throws Exception {
		RestTemplateCustomizer customizer = mock(RestTemplateCustomizer.class);
		RestTemplate template = this.builder.customizers(customizer).build();
		verify(customizer).customize(template);
	}

	@Test
	public void customizersShouldBeAppliedLast() throws Exception {
		RestTemplate template = spy(new RestTemplate());
		this.builder.additionalCustomizers(new RestTemplateCustomizer() {

			@Override
			public void customize(RestTemplate restTemplate) {
				verify(restTemplate).setRequestFactory((ClientHttpRequestFactory) any());
			}

		});
		this.builder.configure(template);
	}

	@Test
	public void customizersShouldReplaceExisting() throws Exception {
		RestTemplateCustomizer customizer1 = mock(RestTemplateCustomizer.class);
		RestTemplateCustomizer customizer2 = mock(RestTemplateCustomizer.class);
		RestTemplate template = this.builder.customizers(customizer1)
				.customizers(Collections.singleton(customizer2)).build();
		verifyZeroInteractions(customizer1);
		verify(customizer2).customize(template);
	}

	@Test
	public void additionalCustomizersWhenCustomizersAreNullShouldThrowException()
			throws Exception {
		this.thrown.expect(IllegalArgumentException.class);
		this.thrown.expectMessage("RestTemplateCustomizers must not be null");
		this.builder.additionalCustomizers((RestTemplateCustomizer[]) null);
	}

	@Test
	public void additionalCustomizersCollectionWhenCustomizersAreNullShouldThrowException()
			throws Exception {
		this.thrown.expect(IllegalArgumentException.class);
		this.thrown.expectMessage("RestTemplateCustomizers must not be null");
		this.builder.additionalCustomizers((Set<RestTemplateCustomizer>) null);
	}

	@Test
	public void additionalCustomizersShouldAddToExisting() throws Exception {
		RestTemplateCustomizer customizer1 = mock(RestTemplateCustomizer.class);
		RestTemplateCustomizer customizer2 = mock(RestTemplateCustomizer.class);
		RestTemplate template = this.builder.customizers(customizer1)
				.additionalCustomizers(customizer2).build();
		verify(customizer1).customize(template);
		verify(customizer2).customize(template);
	}

	@Test
	public void buildShouldReturnRestTemplate() throws Exception {
		RestTemplate template = this.builder.build();
		assertThat(template.getClass()).isEqualTo(RestTemplate.class);
	}

	@Test
	public void buildClassShouldReturnClassInstance() throws Exception {
		RestTemplateSubclass template = this.builder.build(RestTemplateSubclass.class);
		assertThat(template.getClass()).isEqualTo(RestTemplateSubclass.class);
	}

	@Test
	public void configureShouldApply() throws Exception {
		RestTemplate template = new RestTemplate();
		this.builder.configure(template);
		assertThat(template.getRequestFactory())
				.isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
	}

	public static class RestTemplateSubclass extends RestTemplate {

	}

}
