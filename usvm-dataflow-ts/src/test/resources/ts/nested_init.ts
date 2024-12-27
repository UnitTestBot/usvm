class Foo {
}

class Cat {
    foo: Foo = new Foo();
}

// class Cat {
//     foo: Foo;
//
//     constructor() {
//         this.foo = new Foo();
//     }
// }

function entrypoint() {
    let cat = new Cat();
    infer(cat);
}

function infer(x: any) {
    console.log(x);
}
