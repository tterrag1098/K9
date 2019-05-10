package com.tterrag.k9.util.annotation;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.annotation.Nonnull;
import javax.annotation.meta.TypeQualifierDefault;


@Retention(RUNTIME)
@Target({ TYPE, PACKAGE })
@Documented
@Nonnull
@TypeQualifierDefault({ElementType.METHOD})
public @interface NonNullMethods {
}
