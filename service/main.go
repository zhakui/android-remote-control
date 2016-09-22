package main

import (
	"fmt"
	"golang.org/x/net/websocket"
	"log"
	"net/http"
	"net"
	"io"
	"os"
	"time"
	"strconv"
)

func wsH264(ws *websocket.Conn) {
	socket, err := net.ListenUDP("udp4", &net.UDPAddr{
		IP:net.IPv4(10,8,230,68),
		Port: 5000,
	})
	if err !=nil{
		panic(err)
	}
	fmt.Printf("new socket\n")
  
	msg := make([]byte, 1024*512)
	for {
		fmt.Printf("setp 1 \n")
		time.Sleep(40 * time.Millisecond)
		
		n, _, err := socket.ReadFromUDP(msg)
		fmt.Printf("setp 2 \n")
		fmt.Printf(strconv.Itoa(n))
		err = websocket.Message.Send(ws, msg[:n])
		if err != nil {
			log.Println(err)
			break
		}
	}
	socket.Close()
	log.Println("send over socket\n")
}

func main() {
	http.Handle("/wsh264", websocket.Handler(wsH264))
	http.Handle("/", http.FileServer(http.Dir("./public")))

	err := http.ListenAndServe(":8080", nil)

	if err != nil {
		panic("ListenAndServe: " + err.Error())
	}
}
