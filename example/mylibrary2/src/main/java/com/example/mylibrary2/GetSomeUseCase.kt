package com.example.mylibrary2

import com.android.model.SomeClass

class GetSomeUseCase() {
    fun invoke(): SomeClass {
        return SomeClass(i = 10)
    }
}
