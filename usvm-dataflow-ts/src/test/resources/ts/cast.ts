declare function getData(): any;

interface Data {}

function infer(arg: any){
    console.log(arg);
}

function entrypoint(){
    let x = getData() as Data;
    infer(x);
}
