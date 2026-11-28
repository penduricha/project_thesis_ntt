package com.example.be_web_admin.qbank;

import com.example.be_web_admin.data_init.QBankDataInitializer;
import com.example.be_web_admin.exception.qbank.QBankExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QBankBeanNamesTest {
    @Test
    void renamedComponentsKeepTheirOriginalSpringBeanNames() {
        var registry = new DefaultListableBeanFactory();
        var names = new AnnotationBeanNameGenerator();
        assertEquals("qbankDataInitializer", names.generateBeanName(
                new AnnotatedGenericBeanDefinition(QBankDataInitializer.class), registry));
        assertEquals("qbankExceptionHandler", names.generateBeanName(
                new AnnotatedGenericBeanDefinition(QBankExceptionHandler.class), registry));
    }
}
