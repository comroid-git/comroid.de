package org.comroid.status.entity;

import org.comroid.common.ref.Named;
import org.comroid.status.DependenyObject;
import org.comroid.status.StatusUpdater;
import org.comroid.uniform.ValueType;
import org.comroid.varbind.bind.GroupBind;
import org.comroid.varbind.bind.VarBind;
import org.comroid.varbind.container.DataContainer;

public interface Entity extends DataContainer<DependenyObject>, Named {
    default String getName() {
        return requireNonNull(Bind.Name);
    }

    interface Bind {
        GroupBind<Entity, DependenyObject> Root
                = new GroupBind<>(StatusUpdater.instance.getSerializationAdapter().join(), "entity");
        VarBind.OneStage<String> Name
                = Root.bind1stage("name", ValueType.STRING);
    }
}
