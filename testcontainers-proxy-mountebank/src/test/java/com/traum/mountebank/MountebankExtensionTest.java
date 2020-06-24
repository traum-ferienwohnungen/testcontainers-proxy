package com.traum.mountebank;

import com.traum.mountebank.MountebankExtension.MountebankProxyFactory;
import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.Answers;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MountebankExtensionTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  ExtensionContext extensionContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  MountebankProxyFactory factory;

  MountebankExtension extension;

  @BeforeEach
  void setup() {
    extension = new MountebankExtension(factory);
  }

  @Test
  void noArgAnnotationThrows() throws NoSuchMethodException {
    final Optional<Method> testMethod =
        Optional.of(TestCaseWithMethodAnnotations.class.getDeclaredMethod("annotatedWithNoArgs"));
    BDDMockito.given(extensionContext.getTestMethod()).willReturn(testMethod);
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> extension.beforeEach(extensionContext));
  }

  @Test
  void illegalPlaceholderThrows() throws NoSuchMethodException {
    final Optional<Method> testMethod =
        Optional.of(
            TestCaseWithMethodAnnotations.class.getDeclaredMethod(
                "annotatedWithIllegalPlaceholder"));
    BDDMockito.given(extensionContext.getTestMethod()).willReturn(testMethod);
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> extension.beforeEach(extensionContext));
  }

  @Test
  void initialImpostersFallsBackToClassLevelValue() throws NoSuchMethodException {
    final Optional<Method> testMethod =
        Optional.of(
            TestCaseWithMethodAndClassAnnotations.class.getDeclaredMethod("annotatedWithNoArgs"));
    BDDMockito.given(extensionContext.getTestClass())
        .willReturn(Optional.of(TestCaseWithMethodAndClassAnnotations.class));
    BDDMockito.given(extensionContext.getTestMethod()).willReturn(testMethod);
    Assertions.assertDoesNotThrow(() -> extension.beforeEach(extensionContext));
  }

  static class TestCaseWithMethodAnnotations {

    @MountebankExtension.WithProxy
    void annotatedWithNoArgs() {}

    @MountebankExtension.WithProxy(initialImposters = "src/test/resources/proxy/{clazz.name}.json")
    void annotatedWithIllegalPlaceholder() {}
  }

  @MountebankExtension.WithProxy(initialImposters = "src/test/resources/proxy/{class.name}.json")
  static class TestCaseWithMethodAndClassAnnotations {

    @MountebankExtension.WithProxy
    void annotatedWithNoArgs() {}
  }
}
