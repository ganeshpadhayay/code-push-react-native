import React from 'react';
import {StyleSheet, Text, View} from 'react-native';

const App = () => {
  return (
    <>
      <View style={styles.body}>
        <Text>Hello</Text>
      </View>
    </>
  );
};

const styles = StyleSheet.create({
  body: {
    marginTop: 40,
  },
});

export default App;
