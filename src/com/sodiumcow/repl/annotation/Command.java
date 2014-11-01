package com.sodiumcow.repl.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Command {
    String name();
    String args()    default "";
    String comment() default "";
    int    min()     default 0;   // minimum number of varargs for String...
    int    max()     default 0;   // maximum number of varargs for String...
}
