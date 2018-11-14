import React, { Component } from "react";
import { Platform, StyleSheet, Text, View } from "react-native";
import { keyboardEnabled } from "react-native-active-keyboards";

export default class App extends Component {
  constructor() {
    super();
    this.state = {
      enabled: false
    };
  }

  async componentWillMount() {
    const enabled = await keyboardEnabled("com.rncustomkeyboard");
    console.log("IN APP", enabled);
    this.setState({ enabled });
  }

  renderInstructions = () => {
    if (Platform.OS === "android") {
      return <Text>Add Android instructions here.</Text>;
    } else if (Platform.OS === "ios") {
      return <Text>Add iOS instructions here.</Text>;
    }
  };

  render() {
    console.log("STATE", this.state);
    let { enabled } = this.state;
    return (
      <View style={styles.container}>
        <Text style={styles.welcome}>RN Custom keyboard</Text>
        <Text style={styles.welcome}>Keyboard enabled: {String(enabled)}</Text>
        {enabled === false && this.renderInstructions()}
      </View>
    );
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: "#F5FCFF"
  },
  welcome: {
    fontSize: 20,
    textAlign: "center",
    margin: 10
  },
  instructions: {
    textAlign: "center",
    color: "#333333",
    marginBottom: 5
  }
});
