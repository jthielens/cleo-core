package com.sodiumcow.repl.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Option {
    String name();
    String args()    default "";
    String comment() default "";
}
