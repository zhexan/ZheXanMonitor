
    public void writeRuntimeData(int clientId, RuntimeDetailVO vo){
        if(vo == null) {
            throw new IllegalArgumentException("RuntimeDetailVO must not be null");
        }
        RuntimeData data = new RuntimeData();
        BeanUtils.copyProperties(vo, data);
        data.setTimestamp(new Date(vo.getTimestamp()).toInstant());
        data.setClientId(clientId);
        WriteApiBlocking writeApi = client.getWriteApiBlocking();
        writeApi.writeMeasurement(BUCKET, ORG, WritePrecision.NS, data);
    }
}