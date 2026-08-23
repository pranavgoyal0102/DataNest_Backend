package com.datanest;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Smoke test that the whole context wires up.
 *
 * <p>The two Firebase beans are overridden rather than configured. Overriding the bean
 * definitions means FirebaseConfig's factory methods never run, so no service-account JSON
 * is needed - the alternative would be committing a real RSA private key. Everything else
 * comes from src/test/resources/application.properties.
 */
@SpringBootTest
class DatanestBackendApplicationTests {

	@MockitoBean
	private FirebaseApp firebaseApp;

	@MockitoBean
	private FirebaseAuth firebaseAuth;

	@Test
	void contextLoads() {
	}

}
