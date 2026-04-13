import { Hyperswitch } from 'capacitor-hyperswitch';

window.testEcho = () => {
    const inputValue = document.getElementById("echoInput").value;
    Hyperswitch.echo({ value: inputValue })
}
